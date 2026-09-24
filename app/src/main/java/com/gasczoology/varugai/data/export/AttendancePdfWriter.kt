package com.gasczoology.varugai.data.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.gasczoology.varugai.data.backup.RegisterBundle
import com.gasczoology.varugai.domain.attendance.AttendanceCalculator
import com.gasczoology.varugai.domain.attendance.EligibilityClassifier
import java.io.OutputStream
import java.util.Locale

object AttendancePdfWriter {
    fun write(bundle: RegisterBundle, output: OutputStream) {
        val document = PdfDocument()
        try {
            val rows = bundle.students.sortedBy { it.rosterOrder }.map { student ->
                val totals = AttendanceCalculator.totals(student, bundle.days, bundle.marks)
                val assessment = EligibilityClassifier.assess(totals, bundle.register)
                listOf(
                    student.roll,
                    student.name,
                    totals.presentHours.toString(),
                    totals.odHours.toString(),
                    totals.absentHours.toString(),
                    totals.countedHours.toString(),
                    totals.percentage?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
                    assessment.label,
                )
            }
            val rowsPerPage = 22
            val pageCount = ((rows.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)
            for (pageIndex in 0 until pageCount) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, pageIndex + 1).create())
                val canvas = page.canvas
                val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
                val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

                canvas.drawText("VARUGAI — Attendance Statement", 32f, 34f, title)
                val r = bundle.register
                canvas.drawText(listOf(r.institution, r.department).filter { it.isNotBlank() }.joinToString(" · "), 32f, 52f, text)
                canvas.drawText(listOf(r.courseCode, r.courseTitle, r.className, r.semester, r.academicYear).filter { it.isNotBlank() }.joinToString(" · "), 32f, 68f, text)
                canvas.drawText("P = Present · OD = On Duty (counts as attended) · A = Absent · Counted = completed working hours", 32f, 84f, text)

                val x = floatArrayOf(32f, 78f, 300f, 345f, 390f, 435f, 490f, 555f)
                val headers = listOf("Roll", "Student", "P", "OD", "A", "Counted", "%", "Standing")
                headers.forEachIndexed { i, h -> canvas.drawText(h, x[i], 108f, bold) }

                val start = pageIndex * rowsPerPage
                val end = minOf(rows.size, start + rowsPerPage)
                var y = 128f
                for (i in start until end) {
                    val row = rows[i]
                    row.forEachIndexed { col, value ->
                        val clipped = when (col) {
                            1 -> value.take(34)
                            7 -> value.take(43)
                            else -> value.take(14)
                        }
                        canvas.drawText(clipped, x[col], y, text)
                    }
                    y += 19f
                }
                canvas.drawText("Page ${pageIndex + 1} of $pageCount", 744f, 574f, text)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
}
