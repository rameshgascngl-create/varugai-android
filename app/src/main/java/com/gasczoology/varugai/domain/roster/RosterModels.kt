package com.gasczoology.varugai.domain.roster

import com.gasczoology.varugai.data.db.StudentEntity

data class RollChange(
    val sid: String,
    val oldRoll: String,
    val newRoll: String,
    val name: String,
)

data class RosterImportPlan(
    val registerId: String,
    val existingCount: Int,
    val students: List<StudentEntity>,
    val rollChanges: List<RollChange>,
    val omittedWithoutAttendance: List<StudentEntity>,
    val omittedWithAttendance: List<StudentEntity>,
) {
    val incomingCount: Int get() = students.size
    val sizeDelta: Int get() = incomingCount - existingCount
    val isBlocked: Boolean get() = omittedWithAttendance.isNotEmpty()
}

data class DeleteStudentResult(
    val deleted: Boolean,
    val attendanceRows: Int,
)
