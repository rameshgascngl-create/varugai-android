package com.gasczoology.varugai.data.export

import com.gasczoology.varugai.data.backup.RegisterBundle
import com.gasczoology.varugai.data.db.AttendanceMarkEntity
import com.gasczoology.varugai.data.db.AuditEventEntity
import com.gasczoology.varugai.domain.attendance.AttendanceCalculator
import com.gasczoology.varugai.domain.attendance.EligibilityClassifier
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NativeExportWriters {
    fun summaryCsv(bundle: RegisterBundle): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Roll", "Register No", "Student Name", "Present Hours", "OD Hours", "Absent Hours", "Counted Hours", "Equivalent Days", "Attendance %", "Standing", "Near Threshold")
        bundle.students.sortedBy { it.rosterOrder }.forEach { student ->
            val totals = AttendanceCalculator.totals(student, bundle.days, bundle.marks)
            val assessment = EligibilityClassifier.assess(totals, bundle.register)
            val equivalentDays = if (bundle.register.defaultHours > 0) totals.countedHours.toDouble() / bundle.register.defaultHours else 0.0
            rows += listOf(
                student.roll,
                student.registerNumber,
                student.name,
                totals.presentHours.toString(),
                totals.odHours.toString(),
                totals.absentHours.toString(),
                totals.countedHours.toString(),
                fmt(equivalentDays),
                totals.percentage?.let(::fmt).orEmpty(),
                assessment.label,
                if (assessment.nearThreshold) "YES" else "NO",
            )
        }
        return rows.joinToString("\n") { row -> row.joinToString(",") { csv(it) } } + "\n"
    }

    fun auditCsv(audits: List<AuditEventEntity>): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Timestamp", "Kind", "Date", "Student ID", "Message")
        audits.sortedBy { it.timestamp }.forEach { event ->
            rows += listOf(
                event.timestamp.toString(),
                event.kind,
                event.date.orEmpty(),
                event.sid.orEmpty(),
                event.message,
            )
        }
        return rows.joinToString("\n") { row -> row.joinToString(",") { csv(it) } } + "\n"
    }

    fun attendanceXlsx(bundle: RegisterBundle, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            writePart(zip, "[Content_Types].xml", contentTypes())
            writePart(zip, "_rels/.rels", rootRels())
            writePart(zip, "xl/workbook.xml", workbook())
            writePart(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            writePart(zip, "xl/worksheets/sheet1.xml", summarySheet(bundle))
            writePart(zip, "xl/worksheets/sheet2.xml", calendarSheet(bundle))
            writePart(zip, "xl/worksheets/sheet3.xml", marksSheet(bundle))
            writePart(zip, "xl/worksheets/sheet4.xml", auditSheet(bundle))
        }
    }

    private fun summarySheet(bundle: RegisterBundle): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Roll", "Register No", "Student Name", "Present", "OD", "Absent", "Counted", "Equivalent Days", "Attendance %", "Standing", "Near Threshold")
        bundle.students.sortedBy { it.rosterOrder }.forEach { student ->
            val totals = AttendanceCalculator.totals(student, bundle.days, bundle.marks)
            val assessment = EligibilityClassifier.assess(totals, bundle.register)
            val equivalentDays = if (bundle.register.defaultHours > 0) totals.countedHours.toDouble() / bundle.register.defaultHours else 0.0
            rows += listOf(
                student.roll, student.registerNumber, student.name,
                totals.presentHours.toString(), totals.odHours.toString(), totals.absentHours.toString(),
                totals.countedHours.toString(), fmt(equivalentDays), totals.percentage?.let(::fmt).orEmpty(),
                assessment.label, if (assessment.nearThreshold) "YES" else "NO",
            )
        }
        return sheetXml(rows)
    }

    private fun calendarSheet(bundle: RegisterBundle): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Date", "Hours", "Working", "Completed", "Note")
        bundle.days.sortedBy { it.date }.forEach { day ->
            rows += listOf(day.date, day.hours.toString(), if (day.isWorking) "YES" else "NO", if (day.isComplete) "YES" else "NO", day.note)
        }
        return sheetXml(rows)
    }

    private fun marksSheet(bundle: RegisterBundle): String {
        val students = bundle.students.associateBy { it.sid }
        val rows = mutableListOf<List<String>>()
        rows += listOf("Date", "Hour", "Roll", "Register No", "Student Name", "Status")
        bundle.marks.sortedWith(compareBy<AttendanceMarkEntity> { it.date }.thenBy { it.hourIndex }.thenBy { students[it.sid]?.rosterOrder ?: Int.MAX_VALUE })
            .forEach { mark ->
                val student = students[mark.sid]
                rows += listOf(
                    mark.date, mark.hourIndex.toString(), student?.roll.orEmpty(), student?.registerNumber.orEmpty(),
                    student?.name.orEmpty(), mark.status,
                )
            }
        return sheetXml(rows)
    }

    private fun auditSheet(bundle: RegisterBundle): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf("Timestamp", "Kind", "Date", "Student ID", "Message")
        bundle.audits.sortedBy { it.timestamp }.forEach { event ->
            rows += listOf(event.timestamp.toString(), event.kind, event.date.orEmpty(), event.sid.orEmpty(), event.message)
        }
        return sheetXml(rows)
    }

    private fun sheetXml(rows: List<List<String>>): String {
        val body = rows.mapIndexed { rowIndex, cells ->
            val rowNo = rowIndex + 1
            val xmlCells = cells.mapIndexed { colIndex, value ->
                val ref = columnName(colIndex) + rowNo
                "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>"
            }.joinToString("")
            "<row r=\"$rowNo\">$xmlCells</row>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Summary" sheetId="1" r:id="rId1"/>
<sheet name="Calendar" sheetId="2" r:id="rId2"/>
<sheet name="Attendance" sheetId="3" r:id="rId3"/>
<sheet name="Audit" sheetId="4" r:id="rId4"/>
</sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
<Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
</Relationships>"""

    private fun writePart(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val out = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            out.append(('A'.code + r).toChar())
            n = (n - 1) / 26
        }
        return out.reverse().toString()
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    private fun xml(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
