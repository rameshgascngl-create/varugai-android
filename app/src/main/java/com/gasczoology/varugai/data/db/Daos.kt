package com.gasczoology.varugai.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RegisterDao {
    @Query("SELECT * FROM registers ORDER BY createdAt")
    fun observeAll(): Flow<List<RegisterEntity>>

    @Query("SELECT * FROM registers ORDER BY createdAt")
    suspend fun getAll(): List<RegisterEntity>

    @Query("SELECT * FROM registers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RegisterEntity?

    // Do not use INSERT OR REPLACE here: deleting/reinserting a register can
    // cascade-delete its roster/calendar/attendance. @Upsert performs a real update.
    @Upsert
    suspend fun upsert(entity: RegisterEntity)

    @Update
    suspend fun update(entity: RegisterEntity)

    @Delete
    suspend fun delete(entity: RegisterEntity)
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE registerId = :registerId ORDER BY rosterOrder, roll")
    fun observeForRegister(registerId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE registerId = :registerId ORDER BY rosterOrder, roll")
    suspend fun getForRegister(registerId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE registerId = :registerId AND sid = :sid LIMIT 1")
    suspend fun getBySid(registerId: String, sid: String): StudentEntity?

    @Upsert
    suspend fun upsert(entity: StudentEntity)

    @Upsert
    suspend fun upsertAll(items: List<StudentEntity>)

    @Query("DELETE FROM students WHERE registerId = :registerId AND sid = :sid")
    suspend fun deleteBySid(registerId: String, sid: String)

    @Query("DELETE FROM students WHERE registerId = :registerId")
    suspend fun deleteForRegister(registerId: String)
}

@Dao
interface TeachingDayDao {
    @Query("SELECT * FROM teaching_days WHERE registerId = :registerId ORDER BY date")
    fun observeForRegister(registerId: String): Flow<List<TeachingDayEntity>>

    @Query("SELECT * FROM teaching_days WHERE registerId = :registerId ORDER BY date")
    suspend fun getForRegister(registerId: String): List<TeachingDayEntity>

    @Query("SELECT * FROM teaching_days WHERE registerId = :registerId AND date = :date LIMIT 1")
    suspend fun getByDate(registerId: String, date: String): TeachingDayEntity?

    @Upsert
    suspend fun upsert(item: TeachingDayEntity)

    @Upsert
    suspend fun upsertAll(items: List<TeachingDayEntity>)

    @Query("DELETE FROM teaching_days WHERE registerId = :registerId")
    suspend fun deleteForRegister(registerId: String)
}

@Dao
interface AttendanceMarkDao {
    @Query("SELECT * FROM attendance_marks WHERE registerId = :registerId")
    fun observeForRegister(registerId: String): Flow<List<AttendanceMarkEntity>>

    @Query("SELECT * FROM attendance_marks WHERE registerId = :registerId")
    suspend fun getForRegister(registerId: String): List<AttendanceMarkEntity>

    @Query("SELECT * FROM attendance_marks WHERE registerId = :registerId AND date = :date")
    suspend fun getForDay(registerId: String, date: String): List<AttendanceMarkEntity>

    @Query("SELECT COUNT(*) FROM attendance_marks WHERE registerId = :registerId AND sid = :sid")
    suspend fun countForStudent(registerId: String, sid: String): Int

    @Query("SELECT DISTINCT sid FROM attendance_marks WHERE registerId = :registerId")
    suspend fun getStudentIdsWithAttendance(registerId: String): List<String>

    @Upsert
    suspend fun upsert(item: AttendanceMarkEntity)

    @Upsert
    suspend fun upsertAll(items: List<AttendanceMarkEntity>)

    @Query("DELETE FROM attendance_marks WHERE registerId = :registerId AND date = :date AND sid = :sid AND hourIndex = :hourIndex")
    suspend fun deleteCell(registerId: String, date: String, sid: String, hourIndex: Int)

    @Query("DELETE FROM attendance_marks WHERE registerId = :registerId AND date = :date AND hourIndex = :hourIndex")
    suspend fun deleteHour(registerId: String, date: String, hourIndex: Int)

    @Query("DELETE FROM attendance_marks WHERE registerId = :registerId AND date = :date")
    suspend fun deleteDay(registerId: String, date: String)

    @Query("DELETE FROM attendance_marks WHERE registerId = :registerId")
    suspend fun deleteForRegister(registerId: String)
}

@Dao
interface AuditEventDao {
    @Query("SELECT * FROM audit_events WHERE registerId = :registerId ORDER BY timestamp DESC")
    fun observeForRegister(registerId: String): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE registerId = :registerId ORDER BY timestamp")
    suspend fun getForRegister(registerId: String): List<AuditEventEntity>

    @Insert
    suspend fun insert(event: AuditEventEntity)

    @Insert
    suspend fun insertAll(events: List<AuditEventEntity>)

    @Query("DELETE FROM audit_events WHERE registerId = :registerId")
    suspend fun deleteForRegister(registerId: String)
}
