package com.gasczoology.varugai.data.backup

import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.AuditEventEntity
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import com.gasczoology.varugai.legacy.LegacyChecksum
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

data class RegisterBundle(
    val register: RegisterEntity,
    val students: List<StudentEntity>,
    val days: List<TeachingDayEntity>,
    val marks: List<AttendanceMarkEntity>,
    val audits: List<AuditEventEntity>,
)

data class BackupImport(
    val bundle: RegisterBundle,
    val schema: Int,
    val sourceLabel: String,
    val warning: String? = null,
)

object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val pretty = Json { prettyPrint = true; explicitNulls = false }

    fun createNative(bundle: RegisterBundle): String {
        val data = bundleToJson(bundle)
        val payload = data.toString()
        val envelope = buildJsonObject {
            put("format", "varugai-backup")
            put("schema", 3)
            put("appVersion", "16.0.2")
            put("created", Instant.now().toString())
            put("register", registerLabel(bundle.register))
            put("students", bundle.students.size)
            put("checksumType", "sha256")
            put("checksum", sha256(payload))
            put("data", data)
        }
        return pretty.encodeToString(JsonElement.serializer(), envelope)
    }

    fun decode(raw: String): BackupImport {
        require(raw.length <= 20 * 1024 * 1024) { "Backup is too large to import safely." }
        val root = json.parseToJsonElement(raw).jsonObject
        require(root.str("format") == "varugai-backup") { "This is not a VARUGAI backup file." }
        val schema = root.int("schema", 0)
        require(schema in 1..3) { "Backup schema $schema is not supported by VARUGAI 16." }
        val data = root["data"]?.jsonObject ?: error("The backup contains no register data.")

        return if (schema == 3) {
            val expected = root.str("checksum")
            require(root.str("checksumType").lowercase() == "sha256") { "Unsupported backup checksum type." }
            val actual = sha256(data.toString())
            require(expected.equals(actual, ignoreCase = true)) {
                "Checksum mismatch — the backup was altered or truncated. Nothing has been restored."
            }
            BackupImport(
                bundle = nativeFromJson(data),
                schema = 3,
                sourceLabel = "VARUGAI ${root.str("appVersion", "16.x")} native backup",
            )
        } else {
            val expected = root.str("checksum")
            if (expected.isNotBlank()) {
                val rawData = extractDataValue(raw)
                val canonical = stripWhitespaceOutsideStrings(rawData)
                val actual = LegacyChecksum.fnv1a32(canonical)
                require(expected.equals(actual, ignoreCase = true)) {
                    "Legacy checksum mismatch — the backup was altered or truncated. Nothing has been restored."
                }
            }
            BackupImport(
                bundle = legacyFromJson(data),
                schema = schema,
                sourceLabel = "VARUGAI ${root.str("appVersion", "15.x")} schema-$schema backup",
                warning = if (schema < 2) "Older backup format imported; verify totals against the source register." else null,
            )
        }
    }

    private fun bundleToJson(bundle: RegisterBundle): JsonObject = buildJsonObject {
        put("register", registerJson(bundle.register))
        put("students", buildJsonArray { bundle.students.sortedBy { it.rosterOrder }.forEach { add(studentJson(it)) } })
        put("days", buildJsonArray { bundle.days.sortedBy { it.date }.forEach { add(dayJson(it)) } })
        put("marks", buildJsonArray {
            bundle.marks.sortedWith(compareBy<AttendanceMarkEntity> { it.date }.thenBy { it.sid }.thenBy { it.hourIndex })
                .forEach { add(markJson(it)) }
        })
        put("audit", buildJsonArray { bundle.audits.sortedBy { it.timestamp }.forEach { add(auditJson(it)) } })
    }

    private fun registerJson(r: RegisterEntity) = buildJsonObject {
        put("institution", r.institution); put("department", r.department); put("faculty", r.faculty)
        put("courseCode", r.courseCode); put("courseTitle", r.courseTitle); put("className", r.className)
        put("semester", r.semester); put("academicYear", r.academicYear)
        put("startDate", r.startDate); put("endDate", r.endDate); put("defaultHours", r.defaultHours)
        put("passMark", r.passMark); put("condonationFeeFloor", r.condonationFeeFloor)
        put("condonationMedicalFloor", r.condonationMedicalFloor); put("verifyBandPoints", r.verifyBandPoints)
        put("minimumCountedHours", r.minimumCountedHours); put("ruleSetLabel", r.ruleSetLabel)
        put("calendarWeekdaysCsv", r.calendarWeekdaysCsv)
    }

    private fun studentJson(s: StudentEntity) = buildJsonObject {
        put("sid", s.sid); put("roll", s.roll); put("registerNumber", s.registerNumber); put("name", s.name)
        put("admissionDate", s.admissionDate); put("attendanceEndDate", s.attendanceEndDate); put("rosterOrder", s.rosterOrder)
    }

    private fun dayJson(d: TeachingDayEntity) = buildJsonObject {
        put("date", d.date); put("hours", d.hours); put("isWorking", d.isWorking); put("isComplete", d.isComplete); put("note", d.note)
    }

    private fun markJson(m: AttendanceMarkEntity) = buildJsonObject {
        put("date", m.date); put("sid", m.sid); put("hourIndex", m.hourIndex); put("status", m.status)
    }

    private fun auditJson(a: AuditEventEntity) = buildJsonObject {
        put("timestamp", a.timestamp); put("kind", a.kind); a.date?.let { put("date", it) }
        a.sid?.let { put("sid", it) }; put("message", a.message)
    }

    private fun nativeFromJson(data: JsonObject): RegisterBundle {
        val r = data["register"]?.jsonObject ?: error("Native backup has no register metadata.")
        val now = System.currentTimeMillis()
        val register = RegisterEntity(
            id = "imported",
            institution = r.str("institution"), department = r.str("department"), faculty = r.str("faculty"),
            courseCode = r.str("courseCode"), courseTitle = r.str("courseTitle"), className = r.str("className"),
            semester = r.str("semester"), academicYear = r.str("academicYear"), startDate = r.str("startDate"), endDate = r.str("endDate"),
            defaultHours = r.int("defaultHours", 5), passMark = r.double("passMark", 75.0),
            condonationFeeFloor = r.double("condonationFeeFloor", 65.0), condonationMedicalFloor = r.double("condonationMedicalFloor", 50.0),
            verifyBandPoints = r.double("verifyBandPoints", 3.0), minimumCountedHours = r.int("minimumCountedHours", 50),
            ruleSetLabel = r.str("ruleSetLabel"), calendarWeekdaysCsv = r.str("calendarWeekdaysCsv", "1,2,3,4,5"),
            createdAt = now, updatedAt = now,
        )
        val students = data.array("students").mapIndexed { index, e ->
            val o = e.jsonObject
            StudentEntity(
                "imported", o.str("sid", "imported-${index + 1}"), o.str("roll"), o.str("registerNumber"),
                o.str("name"), o.str("admissionDate"), o.str("attendanceEndDate"), o.int("rosterOrder", index),
            )
        }
        val days = data.array("days").map { e ->
            val o = e.jsonObject
            TeachingDayEntity("imported", o.str("date"), o.int("hours", register.defaultHours), o.bool("isWorking", true), o.bool("isComplete", false), o.str("note"))
        }
        val marks = data.array("marks").map { e ->
            val o = e.jsonObject
            AttendanceMarkEntity("imported", o.str("date"), o.str("sid"), o.int("hourIndex", 1), o.str("status"))
        }
        val audits = data.array("audit").map { e ->
            val o = e.jsonObject
            AuditEventEntity(
                registerId = "imported", timestamp = o.long("timestamp", now), kind = o.str("kind", "restore"),
                date = o.optStr("date"), sid = o.optStr("sid"), message = o.str("message"),
            )
        }
        validateBundle(register, students, days, marks)
        return RegisterBundle(register, students, days, marks, audits)
    }

    private fun legacyFromJson(data: JsonObject): RegisterBundle {
        val meta = data["meta"]?.jsonObject ?: JsonObject(emptyMap())
        val now = System.currentTimeMillis()
        val daysObject = data["days"]?.jsonObject ?: error("Legacy backup contains no calendar.")
        val inferredWeekdays = daysObject.entries.mapNotNull { (date, value) ->
            if (value.jsonObject.bool("on", false)) runCatching { LocalDate.parse(date).dayOfWeek.value }.getOrNull() else null
        }.toSortedSet().joinToString(",").ifBlank { "1,2,3,4,5" }
        val register = RegisterEntity(
            id = "imported",
            institution = meta.str("institution"), department = meta.str("department"), faculty = meta.str("faculty"),
            courseCode = meta.str("courseCode"), courseTitle = meta.str("courseTitle"), className = meta.str("className"),
            semester = meta.str("semester"), academicYear = meta.str("acadYear"), startDate = meta.str("startDate"), endDate = meta.str("endDate"),
            defaultHours = meta.int("defHours", 5), passMark = meta.double("passMark", 75.0),
            condonationFeeFloor = meta.double("condFee", meta.double("condFloor", 65.0)),
            condonationMedicalFloor = meta.double("condMed", 50.0), verifyBandPoints = meta.double("bandPts", 3.0),
            minimumCountedHours = meta.int("minHours", 50), ruleSetLabel = meta.str("rulesetLabel"),
            calendarWeekdaysCsv = inferredWeekdays, createdAt = now, updatedAt = now,
        )
        val rosterArray = data["roster"]?.jsonArray ?: error("Legacy backup contains no roster.")
        val students = rosterArray.mapIndexed { index, e ->
            val o = e.jsonObject
            StudentEntity(
                registerId = "imported",
                sid = o.str("sid").ifBlank { "legacy-${index + 1}" },
                roll = o.str("roll"),
                registerNumber = o.str("regno"),
                name = o.str("name"),
                admissionDate = o.optStr("from").orEmpty(),
                attendanceEndDate = o.optStr("to").orEmpty(),
                rosterOrder = index,
            )
        }
        val sidByRoll = students.associate { it.roll to it.sid }
        val days = daysObject.entries.map { (date, e) ->
            val o = e.jsonObject
            TeachingDayEntity(
                registerId = "imported", date = date, hours = o.int("hours", register.defaultHours),
                isWorking = o.bool("on", false), isComplete = o.bool("done", false), note = o.str("note"),
            )
        }.sortedBy { it.date }
        val marks = mutableListOf<AttendanceMarkEntity>()
        val markRoot = data["marks"]?.jsonObject ?: JsonObject(emptyMap())
        markRoot.forEach { (date, dayElement) ->
            val dayMarks = dayElement.jsonObject
            dayMarks.forEach markLoop@ { (legacyKey, encodedElement) ->
                val sid = students.firstOrNull { it.sid == legacyKey }?.sid ?: sidByRoll[legacyKey] ?: return@markLoop
                val encoded = encodedElement.jsonPrimitive.content
                encoded.forEachIndexed { index, ch ->
                    if (ch == 'P' || ch == 'A' || ch == 'O') {
                        marks.add(AttendanceMarkEntity("imported", date, sid, index + 1, ch.toString()))
                    }
                }
            }
        }
        val audits = (data["audit"] as? JsonArray).orEmpty().map { e ->
            val o = e.jsonObject
            val kind = o.str("kind", "legacy")
            val note = o.str("note").ifBlank {
                if (kind == "cell") "Legacy correction H${o.int("hour", 0)}: ${o.str("from")} → ${o.str("to")}" else "Legacy audit event"
            }
            AuditEventEntity(
                registerId = "imported", timestamp = o.long("ts", now), kind = kind,
                date = o.optStr("date"), sid = o.optStr("sid")?.let { sidByRoll[it] ?: it }, message = note,
            )
        }
        validateBundle(register, students, days, marks)
        return RegisterBundle(register, students, days, marks, audits)
    }

    private fun validateBundle(
        register: RegisterEntity,
        students: List<StudentEntity>,
        days: List<TeachingDayEntity>,
        marks: List<AttendanceMarkEntity>,
    ) {
        require(students.all { it.roll.isNotBlank() && it.name.isNotBlank() }) { "Backup contains a student with missing roll or name." }
        require(students.map { it.sid }.distinct().size == students.size) { "Backup contains duplicate student IDs." }
        require(students.map { it.roll }.distinct().size == students.size) { "Backup contains duplicate roll numbers." }
        val dayByDate = days.associateBy { it.date }
        require(days.map { it.date }.distinct().size == days.size) { "Backup contains duplicate calendar dates." }
        val sids = students.mapTo(hashSetOf()) { it.sid }
        marks.forEach { m ->
            val day = dayByDate[m.date] ?: error("Backup mark refers to missing calendar date ${m.date}.")
            require(m.sid in sids) { "Backup mark refers to an unknown student." }
            require(m.hourIndex in 1..day.hours) { "Backup mark has an invalid hour index." }
            require(m.status in setOf("P", "A", "O")) { "Backup contains an invalid attendance state." }
        }
        require(register.passMark in 0.0..100.0 && register.condonationFeeFloor in 0.0..100.0 && register.condonationMedicalFloor in 0.0..100.0) {
            "Backup attendance thresholds are invalid."
        }
    }

    private fun registerLabel(r: RegisterEntity): String =
        listOf(r.courseCode, r.className, r.semester).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { r.courseTitle.ifBlank { "Untitled register" } }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun extractDataValue(raw: String): String {
        val marker = "\"data\""
        val markerIndex = raw.indexOf(marker)
        require(markerIndex >= 0) { "Backup data field not found." }
        var i = markerIndex + marker.length
        while (i < raw.length && raw[i].isWhitespace()) i++
        require(i < raw.length && raw[i] == ':') { "Backup data field is malformed." }
        i++
        while (i < raw.length && raw[i].isWhitespace()) i++
        require(i < raw.length && (raw[i] == '{' || raw[i] == '[')) { "Backup data field is malformed." }
        val open = raw[i]
        val close = if (open == '{') '}' else ']'
        val start = i
        var depth = 0
        var inString = false
        var escaped = false
        while (i < raw.length) {
            val ch = raw[i]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else {
                if (ch == '"') inString = true
                else if (ch == open) depth++
                else if (ch == close) {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
            i++
        }
        error("Backup data field is truncated.")
    }

    private fun stripWhitespaceOutsideStrings(value: String): String {
        val out = StringBuilder(value.length)
        var inString = false
        var escaped = false
        for (ch in value) {
            if (inString) {
                out.append(ch)
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else {
                if (ch == '"') { inString = true; out.append(ch) }
                else if (!ch.isWhitespace()) out.append(ch)
            }
        }
        return out.toString()
    }

    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: default

    private fun JsonObject.optStr(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.long(key: String, default: Long): Long =
        this[key]?.jsonPrimitive?.longOrNull ?: default

    private fun JsonObject.double(key: String, default: Double): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: default

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObject.array(key: String): JsonArray =
        this[key] as? JsonArray ?: JsonArray(emptyList())
}
