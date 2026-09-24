package com.gasczoology.varugai.domain.roster

data class RosterImportRow(
    val roll: String,
    val registerNumber: String = "",
    val name: String,
    val admissionDate: String = "",
    val attendanceEndDate: String = "",
)
