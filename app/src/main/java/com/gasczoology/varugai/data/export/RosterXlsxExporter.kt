package com.gasczoology.varugai.data.export

import com.gasczoology.varugai.data.db.StudentEntity
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RosterXlsxExporter {
    fun write(students: List<StudentEntity>, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            part(zip, "[Content_Types].xml", contentTypes())
            part(zip, "_rels/.rels", rootRels())
            part(zip, "xl/workbook.xml", workbook())
            part(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            part(zip, "xl/worksheets/sheet1.xml", sheet(students))
        }
    }

    private fun part(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Roster" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

    private fun sheet(students: List<StudentEntity>): String {
        val rows = buildList {
            add(listOf("Roll", "Reg. No", "Name", "Counts from", "Counts until"))
            students.sortedBy { it.rosterOrder }.forEach {
                add(listOf(it.roll, it.registerNumber, it.name, it.admissionDate, it.attendanceEndDate))
            }
        }
        val body = rows.mapIndexed { rowIndex, cells ->
            val rowNum = rowIndex + 1
            val xmlCells = cells.mapIndexed { colIndex, value ->
                val ref = columnName(colIndex) + rowNum
                "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>"
            }.joinToString("")
            "<row r=\"$rowNum\">$xmlCells</row>"
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$body</sheetData></worksheet>"""
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

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
}
