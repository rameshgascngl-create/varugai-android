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
       