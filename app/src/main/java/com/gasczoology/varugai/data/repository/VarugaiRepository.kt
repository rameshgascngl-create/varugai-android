package com.gasczoology.varugai.data.repository

import androidx.room.withTransaction
import com.gasczoology.varugai.data.db.AuditEventEntity
import com.gasczoology.varugai.data.db.RegisterEntity
import com.gasczoology.varugai.data.db.StudentEntity
import com.gasczoology.varugai.data.db.TeachingDayEntity
import com.gasczoology.varugai.data.db.VarugaiDatabase
import com.gasczoology.varugai.domain.roster.DeleteStudentResult
import com.gasczoology.varugai.domain.roster.RollChange
import com.gasczoology.varugai.domain.roster.RosterImportPlan
import com.gasczoology.varugai.domain.roster.RosterImportRow
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class VarugaiRepository(private val db: VarugaiDatabase) {
    val registers: Flow<List<RegisterEntity>> = db.registerDao().observeAll()

    fun observeTeachingDays(registerId: String): Flow<List<TeachingDayEntity>> =
        db.teachingDayDao().observeForRegister(registerId)

    fun observeStudents(registerId: String): Flow<List<StudentEntity>> =
        db.studentDao().observeForRegister(registerId)

    suspend fun ensureInitialRegister(): RegisterEntity = db.withTransaction {
        db.registerDao().getAll().firstOrNull() ?: RegisterEntity(id = newRegisterId()).also {
            db.registerDao().upsert(it)
        }
    }

    suspend fun getRegister(id: String): RegisterEntity? = db.registerDao().getById(id)

    suspend fun saveRegister(register: RegisterEntity) {
        // @Upsert is deliberate; INSERT OR REPLACE would cascade-delete child rows.
        db.registerDao().upsert(register.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun createRegister(seed: RegisterEntity? = null): RegisterEntity = db.withTransaction {
        val now = System.currentTimeMillis()
        val created = RegisterEntity(
            id = newRegisterId(),
            institution = seed?.institution.orEmpty(),
            department = seed?.department.orEmpty(),
            faculty = seed?.faculty.orEmpty(),
            academicYear = seed?.academicYear.orEmpty(),
            defaultHours = seed?.defaultHours ?: 5,
            passMark = seed?.passMark ?: 75.0,
            condonationFeeFloor = seed?.condonationFeeFloor ?: 65.0,
            condonationMedicalFloor = seed?.condonationMedicalFloor ?: 50.0,
            verifyBandPoints = seed?.verifyBandPoints ?: 3.0,
            minimumCountedHours = seed?.minimumCountedHours ?: 50,
            ruleSetLabel = seed?.ruleSetLabel.orEmpty(),
            calendarWeekdaysCsv = seed?.calendarWeekdaysCsv ?: "1,2,3,4,5",
            createdAt = now,
            updatedAt = now,
        )
        db.registerDao().upsert(created)
        created
    }

    suspend fun duplicateRegister(sourceId: String): RegisterEntity = db.withTransaction {
        val source = requireNotNull(db.registerDao().getById(sourceId)) { "Register not found" }
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = newRegisterId(),
            courseCode = "",
            courseTitle = "",
            createdAt = now,
            updatedAt = now,
        )
        db.registerDao().upsert(copy)

        val students = db.studentDao().getForRegister(sourceId).map { it.copy(registerId = copy.id) }
        if (students.isNotEmpty()) db.studentDao().upsertAll(students)

        val days = db.teachingDayDao().getForRegister(sourceId).map {
            it.copy(registerId = copy.id, isComplete = false)
        }
        if (days.isNotEmpty()) db.teachingDayDao().upsertAll(days)

        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = copy.id,
                timestamp = now,
                kind = "register",
                message = "Register duplicated from $sourceId; roster/calendar copied; attendance not copied",
            )
        )
        copy
    }

    suspend fun deleteRegister(id: String): Boolean = db.withTransaction {
        val all = db.registerDao().getAll()
        if (all.size <= 1) return@withTransaction false
        val target = all.firstOrNull { it.id == id } ?: return@withTransaction false
        db.registerDao().delete(target)
        true
    }

    suspend fun generateCalendar(register: RegisterEntity, weekdays: Set<Int>) = db.withTransaction {
        val start = LocalDate.parse(register.startDate)
        val end = LocalDate.parse(register.endDate)
        require(!end.isBefore(start)) { "End date must not precede start date" }
        require(weekdays.isNotEmpty()) { "Select at least one weekday" }

        val existing = db.teachingDayDao().getForRegister(register.id).associateBy { it.date }
        val generated = buildList {
            var date = start
            while (!date.isAfter(end)) {
                val old = existing[date.toString()]
                add(
                    old ?: TeachingDayEntity(
                        registerId = register.id,
                        date = date.toString(),
                        hours = register.defaultHours,
                        isWorking = date.dayOfWeek.value in weekdays,
                        note = if (date.dayOfWeek.value in weekdays) "" else defaultNonWorkingNote(date.dayOfWeek),
                    )
                )
                date = date.plusDays(1)
            }
        }
        db.teachingDayDao().deleteForRegister(register.id)
        db.teachingDayDao().upsertAll(generated)
        db.registerDao().upsert(register.copy(calendarWeekdaysCsv = weekdays.sorted().joinToString(","), updatedAt = System.currentTimeMillis()))
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = register.id,
                timestamp = System.currentTimeMillis(),
                kind = "calendar",
                message = "Calendar generated for ${generated.size} date(s)",
            )
        )
    }

    suspend fun updateTeachingDay(day: TeachingDayEntity) = db.withTransaction {
        db.teachingDayDao().upsertAll(listOf(day))
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = day.registerId,
                timestamp = System.currentTimeMillis(),
                kind = "day",
                date = day.date,
                message = if (day.isWorking) "Working day set to ${day.hours} hour(s)" else "Marked as non-working day",
            )
        )
    }

    suspend fun addWorkingDay(registerId: String, date: String, hours: Int) = db.withTransaction {
        LocalDate.parse(date)
        require(hours in 1..8) { "Hours must be between 1 and 8" }
        val day = TeachingDayEntity(
            registerId = registerId,
            date = date,
            hours = hours,
            isWorking = true,
            isComplete = false,
            note = "Special working day",
        )
        db.teachingDayDao().upsertAll(listOf(day))
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = registerId,
                timestamp = System.currentTimeMillis(),
                kind = "day",
                date = date,
                message = "Special working day added: $hours hour(s)",
            )
        )
    }

    suspend fun setDayOfWeekWorking(registerId: String, dayOfWeek: Int, working: Boolean) = db.withTransaction {
        require(dayOfWeek in 1..7)
        val days = db.teachingDayDao().getForRegister(registerId)
        val updated = days.map { day ->
            if (LocalDate.parse(day.date).dayOfWeek.value == dayOfWeek) {
                day.copy(
                    isWorking = working,
                    isComplete = if (working) day.isComplete else false,
                    note = if (working) "" else defaultNonWorkingNote(DayOfWeek.of(dayOfWeek)),
                )
            } else day
        }
        if (updated.isNotEmpty()) db.teachingDayDao().upsertAll(updated)
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = registerId,
                timestamp = System.currentTimeMillis(),
                kind = "calendar",
                message = "${DayOfWeek.of(dayOfWeek)} set ${if (working) "working" else "non-working"} in calendar",
            )
        )
    }

    suspend fun copyCalendar(sourceId: String, targetId: String) = db.withTransaction {
        require(sourceId != targetId) { "Source and target registers must differ" }
        val sourceDays = db.teachingDayDao().getForRegister(sourceId)
        db.teachingDayDao().deleteForRegister(targetId)
        db.teachingDayDao().upsertAll(sourceDays.map { it.copy(registerId = targetId, isComplete = false) })
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = targetId,
                timestamp = System.currentTimeMillis(),
                kind = "calendar",
                message = "Calendar copied from $sourceId",
            )
        )
    }

    // ---------- Phase 2: native roster ----------

    suspend fun addStudent(
        registerId: String,
        roll: String,
        registerNumber: String,
        name: String,
        admissionDate: String,
        attendanceEndDate: String,
    ): StudentEntity = db.withTransaction {
        val existing = db.studentDao().getForRegister(registerId)
        val cleanRoll = roll.trim()
        val cleanName = name.trim()
        require(cleanRoll.isNotBlank()) { "Roll number is required." }
        require(cleanName.isNotBlank()) { "Student name is required." }
        validateOptionalDates(admissionDate, attendanceEndDate)
        require(existing.none { it.roll == cleanRoll }) { "Roll number $cleanRoll already exists." }
        val student = StudentEntity(
            registerId = registerId,
            sid = newStudentId(),
            roll = cleanRoll,
            registerNumber = registerNumber.trim(),
            name = cleanName,
            admissionDate = admissionDate.trim(),
            attendanceEndDate = attendanceEndDate.trim(),
            rosterOrder = (existing.maxOfOrNull { it.rosterOrder } ?: -1) + 1,
        )
        db.studentDao().upsert(student)
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = registerId,
                timestamp = System.currentTimeMillis(),
                kind = "roster",
                sid = student.sid,
                message = "Student added: ${student.roll} ${student.name}",
            )
        )
        student
    }

    suspend fun updateStudent(student: StudentEntity) = db.withTransaction {
        val clean = student.copy(
            roll = student.roll.trim(),
            registerNumber = student.registerNumber.trim(),
            name = student.name.trim(),
            admissionDate = student.admissionDate.trim(),
            attendanceEndDate = student.attendanceEndDate.trim(),
        )
        require(clean.roll.isNotBlank()) { "Roll number is required." }
        require(clean.name.isNotBlank()) { "Student name is required." }
        validateOptionalDates(clean.admissionDate, clean.attendanceEndDate)
        val existing = db.studentDao().getForRegister(clean.registerId)
        require(existing.none { it.sid != clean.sid && it.roll == clean.roll }) { "Roll number ${clean.roll} already exists." }
        val before = db.studentDao().getBySid(clean.registerId, clean.sid)
            ?: error("Student not found.")
        db.studentDao().upsert(clean)
        val note = if (before.roll != clean.roll) {
            "Roll ${before.roll} → ${clean.roll} for ${clean.name}; attendance history retained by stable student ID"
        } else "Student details updated: ${clean.roll} ${clean.name}"
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = clean.registerId,
                timestamp = System.currentTimeMillis(),
                kind = "roster",
                sid = clean.sid,
                message = note,
            )
        )
    }

    suspend fun deleteStudent(registerId: String, sid: String): DeleteStudentResult = db.withTransaction {
        val count = db.attendanceMarkDao().countForStudent(registerId, sid)
        if (count > 0) return@withTransaction DeleteStudentResult(false, count)
        val student = db.studentDao().getBySid(registerId, sid) ?: return@withTransaction DeleteStudentResult(false, 0)
        db.studentDao().deleteBySid(registerId, sid)
        normalizeRosterOrder(registerId)
        db.auditEventDao().insert(
            AuditEventEntity(
                registerId = registerId,
                timestamp = System.currentTimeMillis(),
                kind = "roster",
                sid = sid,
                message = "Student removed before attendance was recorded: ${student.roll} ${student.name}",
            )
        )
        DeleteStudentResult(true, 0)
    }

    suspend fun moveStudent(registerId: String, sid: String, delta: Int) = db.withTransaction {
        if (delta == 0) return@withTransaction
        val list = db.studentDao().getForRegister(registerId).toMutableList()
        val from = list.indexOfFirst { it.sid == sid }
        if (from < 0) return@withTransaction
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return@withTransaction
        val item = list.removeAt(from)
        list.add(to, item)
        db.studentDao().upsertAll(list.mapIndexed { index, student -> student.copy(rosterOrder = index) })
    }

    suspend fun previewRosterReplacement(registerId: String, rows: List<RosterImportRow>): RosterImportPlan = db.withTransaction {
        require(rows.isNotEmpty()) { "The roster is empty." }
        val cleaned = rows.map {
            it.copy(
                roll = it.roll.trim(),
                registerNumber = it.registerNumber.trim(),
                name = it.name.trim(),
                admissionDate = it.admissionDate.trim(),
                attendanceEndDate = it.attendanceEndDate.trim(),
            )
        }
        require(cleaned.none { it.roll.isBlank() || it.name.isBlank() }) { "Every row needs a roll number and name." }
        val duplicateRolls = cleaned.groupBy { it.roll }.filterValues { it.size > 1 }.keys
        require(duplicateRolls.isEmpty()) { "Duplicate roll number(s): ${duplicateRolls.take(6).joinToString()}" }
        cleaned.forEach { validateOptionalDates(it.admissionDate, it.attendanceEndDate) }

        val existing = db.studentDao().getForRegister(registerId)
        val existingByRoll = existing.associateBy { it.roll }
        val regGroups = existing.filter { it.registerNumber.isNotBlank() }.groupBy { it.registerNumber }
        val nameGroups = existing.groupBy { normalizedName(it.name) }
        val claimed = mutableSetOf<String>()
        val changes = mutableListOf<RollChange>()

        val mapped = cleaned.mapIndexed { index, row ->
            val oldByRoll = existingByRoll[row.roll]?.takeIf { it.sid !in claimed }
            val oldByReg = row.registerNumber.takeIf { it.isNotBlank() }
                ?.let { regGroups[it] }
                ?.singleOrNull()
                ?.takeIf { it.sid !in claimed }
            val oldByName = nameGroups[normalizedName(row.name)]
                ?.singleOrNull()
                ?.takeIf { it.sid !in claimed }
            val old = oldByRoll ?: oldByReg ?: oldByName
            if (old != null) claimed.add(old.sid)
            if (old != null && old.roll != row.roll) {
                changes.add(RollChange(old.sid, old.roll, row.roll, row.name))
            }
            StudentEntity(
                registerId = registerId,
                sid = old?.sid ?: newStudentId(),
                roll = row.roll,
                registerNumber = row.registerNumber.ifBlank { old?.registerNumber.orEmpty() },
                name = row.name,
                admissionDate = row.admissionDate.ifBlank { old?.admissionDate.orEmpty() },
                attendanceEndDate = row.attendanceEndDate.ifBlank { old?.attendanceEndDate.orEmpty() },
                rosterOrder = index,
            )
        }

        val keptIds = mapped.mapTo(mutableSetOf()) { it.sid }
        val omitted = existing.filter { it.sid !in keptIds }
        val idsWithAttendance = db.attendanceMarkDao().getStudentIdsWithAttendance(registerId).toSet()
        RosterImportPlan(
            registerId = registerId,
            students = mapped,
            rollChanges = changes,
            omittedWithoutAttendance = omitted.filter { it.sid !in idsWithAttendance },
            omittedWithAttendance = omitted.filter { it.sid in idsWithAttendance },
        )
    }

    suspend fun applyRosterReplacement(plan: RosterImportPlan) = db.withTransaction {
        require(!plan.isBlocked) {
            "Import blocked: ${plan.omittedWithAttendance.size} existing student(s) already have attendance but are missing from the new list."
        }
        require(db.registerDao().getById(plan.registerId) != null) { "Register not found." }
        // Revalidate at commit time so a stale preview can never delete a student who
        // acquired attendance after the confirmation dialog was opened.
        val nowMarked = db.attendanceMarkDao().getStudentIdsWithAttendance(plan.registerId).toSet()
        val newlyProtected = plan.omittedWithoutAttendance.filter { it.sid in nowMarked }
        require(newlyProtected.isEmpty()) {
            "Roster changed while import was being reviewed: ${newlyProtected.size} omitted student(s) now have attendance. Re-open the import preview."
        }
        // Matched students are updated in place through @Upsert, so attendance keyed by sid survives.
        // Only omitted students with zero attendance are deleted.
        plan.omittedWithoutAttendance.forEach { db.studentDao().deleteBySid(plan.registerId, it.sid) }
        db.studentDao().upsertAll(plan.students)
        val now = System.currentTimeMillis()
        val events = mutableListOf<AuditEventEntity>()
        plan.rollChanges.forEach { change ->
            events.add(
                AuditEventEntity(
                    registerId = plan.registerId,
                    timestamp = now,
                    kind = "roster",
                    sid = change.sid,
                    message = "Roll ${change.oldRoll} → ${change.newRoll} for ${change.name}; attendance history retained by stable student ID",
                )
            )
        }
        events.add(
            AuditEventEntity(
                registerId = plan.registerId,
                timestamp = now,
                kind = "roster",
                message = "Roster import committed: ${plan.students.size} student(s); ${plan.omittedWithoutAttendance.size} unmarked omitted student(s) removed",
            )
        )
        db.auditEventDao().insertAll(events)
    }

    private suspend fun normalizeRosterOrder(registerId: String) {
        val students = db.studentDao().getForRegister(registerId)
        db.studentDao().upsertAll(students.mapIndexed { index, student -> student.copy(rosterOrder = index) })
    }

    private fun validateOptionalDates(from: String, to: String) {
        val f = from.trim().takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        val t = to.trim().takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        if (f != null && t != null) require(!t.isBefore(f)) { "Counts-until date must not precede counts-from date." }
    }

    private fun normalizedName(value: String): String = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun newRegisterId(): String = "r" + UUID.randomUUID().toString().replace("-", "").take(16)
    private fun newStudentId(): String = "s" + UUID.randomUUID().toString().replace("-", "").take(16)

    private fun defaultNonWorkingNote(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "Saturday"
        DayOfWeek.SUNDAY -> "Sunday"
        else -> "Not a teaching day"
    }
}
