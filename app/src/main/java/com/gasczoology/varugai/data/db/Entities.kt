package com.gasczoology.varugai.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "registers")
data class RegisterEntity(
    @androidx.room.PrimaryKey val id: String,
    val institution: String = "",
    val department: String = "",
    val faculty: String = "",
    val courseCode: String = "",
    val courseTitle: String = "",
    val className: String = "",
    val semester: String = "",
    val academicYear: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val defaultHours: Int = 5,
    val passMark: Double = 75.0,
    val condonationFeeFloor: Double = 65.0,
    val condonationMedicalFloor: Double = 50.0,
    val verifyBandPoints: Double = 3.0,
    val minimumCountedHours: Int = 50,
    val ruleSetLabel: String = "",
    val calendarWeekdaysCsv: String = "1,2,3,4,5",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "students",
    primaryKeys = ["registerId", "sid"],
    foreignKeys = [ForeignKey(
        entity = RegisterEntity::class,
        parentColumns = ["id"],
        childColumns = ["registerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("registerId"), Index(value = ["registerId", "roll"], unique = true)],
)
data class StudentEntity(
    val registerId: String,
    val sid: String,
    val roll: String,
    val registerNumber: String = "",
    val name: String,
    val admissionDate: String = "",
    val attendanceEndDate: String = "",
    val rosterOrder: Int,
)

@Entity(
    tableName = "teaching_days",
    primaryKeys = ["registerId", "date"],
    foreignKeys = [ForeignKey(
        entity = RegisterEntity::class,
        parentColumns = ["id"],
        childColumns = ["registerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("registerId")],
)
data class TeachingDayEntity(
    val registerId: String,
    val date: String,
    val hours: Int,
    val isWorking: Boolean,
    val isComplete: Boolean = false,
    val note: String = "",
)

@Entity(
    tableName = "attendance_marks",
    primaryKeys = ["registerId", "date", "sid", "hourIndex"],
    foreignKeys = [
        ForeignKey(
            entity = RegisterEntity::class,
            parentColumns = ["id"],
            childColumns = ["registerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["registerId", "sid"],
            childColumns = ["registerId", "sid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("registerId"), Index(value = ["registerId", "sid"]), Index(value = ["registerId", "date"])],
)
data class AttendanceMarkEntity(
    val registerId: String,
    val date: String,
    val sid: String,
    val hourIndex: Int,
    val status: String,
)

@Entity(
    tableName = "audit_events",
    foreignKeys = [ForeignKey(
        entity = RegisterEntity::class,
        parentColumns = ["id"],
        childColumns = ["registerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("registerId"), Index(value = ["registerId", "timestamp"])],
)
data class AuditEventEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val registerId: String,
    val timestamp: Long,
    val kind: String,
    val date: String? = null,
    val sid: String? = null,
    val message: String,
)
