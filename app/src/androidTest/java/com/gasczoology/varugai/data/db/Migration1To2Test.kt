package com.gasczoology.varugai.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val dbName = "varugai-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VarugaiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration1To2PreservesStudentsAndAddsAttendanceEndDate() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """CREATE TABLE IF NOT EXISTS registers (
                    id TEXT NOT NULL PRIMARY KEY,
                    institution TEXT NOT NULL, department TEXT NOT NULL, faculty TEXT NOT NULL,
                    courseCode TEXT NOT NULL, courseTitle TEXT NOT NULL, className TEXT NOT NULL,
                    semester TEXT NOT NULL, academicYear TEXT NOT NULL, startDate TEXT NOT NULL,
                    endDate TEXT NOT NULL, defaultHours INTEGER NOT NULL, passMark REAL NOT NULL,
                    condonationFeeFloor REAL NOT NULL, condonationMedicalFloor REAL NOT NULL,
                    verifyBandPoints REAL NOT NULL, minimumCountedHours INTEGER NOT NULL,
                    ruleSetLabel TEXT NOT NULL, calendarWeekdaysCsv TEXT NOT NULL,
                    createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
                )""".trimIndent()
            )
            execSQL(
                """CREATE TABLE IF NOT EXISTS students (
                    registerId TEXT NOT NULL, sid TEXT NOT NULL, roll TEXT NOT NULL,
                    registerNumber TEXT NOT NULL, name TEXT NOT NULL, admissionDate TEXT NOT NULL,
                    rosterOrder INTEGER NOT NULL,
                    PRIMARY KEY(registerId, sid)
                )""".trimIndent()
            )
            execSQL("INSERT INTO registers VALUES ('r1','','','','','','','','','','',5,75,65,50,3,1,'','1,2,3,4,5',1,1)")
            execSQL("INSERT INTO students VALUES ('r1','s1','1','24Z001','Sample Student','',0)")
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, false, VarugaiDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name, attendanceEndDate FROM students WHERE sid='s1'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Sample Student", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
        }
    }
}
