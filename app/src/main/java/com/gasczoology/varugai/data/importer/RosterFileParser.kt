package com.gasczoology.varugai.data.importer

import com.gasczoology.varugai.domain.roster.RosterImportRow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Pure JVM/Android roster readers. No WebView and no external office-file library. */
object RosterFileParser {
    data class ColumnGuess(val roll: Int, val registerNumber: Int, val name: Int)

    fun parseByFileName(fileName: String?, input: InputStream): List<List<String>> {
        val lower = fileName.orEmpty().lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".xlsx") -> parseXlsx(input)
            lower.endsWith(".docx") -> parseDocx(input)
            lower.endsWith(".doc") -> error("Old .doc files are not supported. Open the file in Word and save it as .docx.")
            lower.endsWith(".xls") -> error("Old .xls files are not supported. Save the sheet as .xlsx or CSV first.")
            else -> parseDelimited(input.readBytesLimited(MAX_TEXT_BYTES).toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        }
    }

    fun parseDelimited(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun finishField() {
            row.add(field.toString().trim())
            field.setLength(0)
        }
        fun finishRow() {
            finishField()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else quoted = false
                } else field.append(ch)
            } else {
                when (ch) {
                    '"' -> quoted = true
                    ',', '\t' -> finishField()
                    '\n' -> finishRow()
                    '\r' -> Unit
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rectangular(rows)
    }

    fun parsePastedRoster(text: String, hasRegisterNumber: Boolean): List<RosterImportRow> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "Paste at least one student." }
        val out = mutableListOf<RosterImportRow>()
        lines.forEachIndexed { index, line ->
            val parts = parseDelimited(line).firstOrNull().orEmpty().toMutableList()
            val roll: String
            val regNo: String
            val name: String
            if (hasRegisterNumber) {
                require(parts.size >= 3) { "Line ${index + 1} needs roll, register number, name." }
                roll = parts.removeAt(0).trim()
                regNo = parts.removeAt(0).trim()
                name = parts.joinToString(",").trim()
            } else if (parts.size >= 2) {
                roll = parts.removeAt(0).trim()
                regNo = ""
                name = parts.joinToString(",").trim()
            } else {
                roll = (index + 1).toString()
                regNo = ""
                name = line
            }
            require(roll.isNotBlank() && name.isNotBlank()) { "Line ${index + 1} is not a valid roster row." }
            out.add(RosterImportRow(roll = roll, registerNumber = regNo, name = name))
        }
        validateRows(out)
        return out
    }

    fun guessColumns(rows: List<List<String>>, hasHeader: Boolean): ColumnGuess {
        require(rows.isNotEmpty()) { "No rows found." }
        val width = rows.maxOf { it.size }
        val header = if (hasHeader) rows.first().map { it.lowercase(Locale.ROOT) } else emptyList()
        val body = rows.drop(if (hasHeader) 1 else 0)
        fun find(re: Regex): Int = header.indexOfFirst { re.containsMatchIn(it) }
        var roll = find(Regex("roll|s\\.?\\s*no|serial|sl", RegexOption.IGNORE_CASE))
        var reg = find(Regex("reg", RegexOption.IGNORE_CASE))
        var name = find(Regex("name|student", RegexOption.IGNORE_CASE))
        if (name < 0) {
            var bestIndex = 0
            var bestScore = -1.0
            for (c in 0 until width) {
                val score = body.sumOf { it.getOrElse(c) { "" }.length }.toDouble() / body.size.coerceAtLeast(1)
                if (score > bestScore) { bestScore = score; bestIndex = c }
            }
            name = bestIndex
        }
        if (roll < 0) {
            for (c in 0 until width) {
                if (c == name) continue
                val numeric = body.count { it.getOrElse(c) { "" }.matches(Regex("\\d{1,4}")) }
                if (body.isNotEmpty() && numeric > body.size * 0.7) { roll = c; break }
            }
        }
        if (reg >= width) reg = -1
        return ColumnGuess(roll = roll, registerNumber = reg, name = name.coerceIn(0, width - 1))
    }

    fun mapColumns(
        rows: List<List<String>>,
        hasHeader: Boolean,
        rollColumn: Int,
        registerNumberColumn: Int,
        nameColumn: Int,
    ): List<RosterImportRow> {
        val body = rows.drop(if (hasHeader) 1 else 0)
        val mapped = body.mapIndexedNotNull { index, row ->
            val name = row.getOrElse(nameColumn) { "" }.trim()
            if (name.isBlank()) return@mapIndexedNotNull null
            val roll = if (rollColumn >= 0) row.getOrElse(rollColumn) { "" }.trim().ifBlank { (index + 1).toString() }
            else (index + 1).toString()
            val reg = if (registerNumberColumn >= 0) row.getOrElse(registerNumberColumn) { "" }.trim() else ""
            RosterImportRow(roll = roll, registerNumber = reg, name = name)
        }
        require(mapped.isNotEmpty()) { "No student names were found in the selected column." }
        validateRows(mapped)
        return mapped
    }

    fun validateRows(rows: List<RosterImportRow>) {
        val duplicateRolls = rows.groupBy { it.roll.trim() }.filterKeys { it.isNotBlank() }.filterValues { it.size > 1 }.keys
        require(duplicateRolls.isEmpty()) { "Duplicate roll number(s): ${duplicateRolls.take(6).joinToString()}" }
        require(rows.none { it.roll.isBlank() || it.name.isBlank() }) { "Every student must have a roll number and name." }
    }

    fun parseXlsx(input: InputStream): List<List<String>> {
        val entries = unzip(input) { name ->
            name == "xl/sharedStrings.xml" || Regex("xl/worksheets/sheet\\d+\\.xml").matches(name)
        }
        val shared = entries["xl/sharedStrings.xml"]?.let { bytes ->
            val doc = xml(bytes)
            descendants(doc.documentElement, "si").map { si -> descendants(si, "t").joinToString("") { it.textContent.orEmpty() } }
        }.orEmpty()
        val sheetName = entries.keys.filter { Regex("xl/worksheets/sheet\\d+\\.xml").matches(it) }.sorted().firstOrNull()
            ?: error("No worksheet found in that .xlsx file.")
        val doc = xml(entries.getValue(sheetName))
        val rows = mutableListOf<List<String>>()
        for (rowNode in descendants(doc.documentElement, "row")) {
            val out = mutableListOf<String>()
            for (cell in childElements(rowNode, "c")) {
                val ref = cell.getAttribute("r").ifBlank { "A1" }
                val index = columnIndex(ref)
                while (out.size <= index) out.add("")
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "s" -> {
                        val n = descendants(cell, "v").firstOrNull()?.textContent?.toIntOrNull() ?: 0
                        shared.getOrElse(n) { "" }
                    }
                    "inlineStr" -> descendants(cell, "t").joinToString("") { it.textContent.orEmpty() }
                    else -> descendants(cell, "v").firstOrNull()?.textContent.orEmpty()
                }
                out[index] = value.trim()
            }
            if (out.any { it.isNotBlank() }) rows.add(out)
        }
        require(rows.isNotEmpty()) { "The workbook contains no readable rows." }
        return rectangular(rows)
    }

    fun parseDocx(input: InputStream): List<List<String>> {
        val entries = unzip(input) { it == "word/document.xml" }
        val bytes = entries["word/document.xml"]
            ?: error("No document body found. If this is an old .doc file, save it as .docx first.")
        val doc = xml(bytes)
        val root = doc.documentElement
        val tableRows = descendants(root, "tr")
        var rows: List<List<String>> = if (tableRows.isNotEmpty()) {
            tableRows.mapNotNull { tr ->
                val cells = childElements(tr, "tc").map { textOf(it) }
                cells.takeIf { it.any(String::isNotBlank) }
            }
        } else {
            descendants(root, "p").mapNotNull { p -> textOf(p).takeIf(String::isNotBlank)?.let { listOf(it) } }
        }
        if (rows.isNotEmpty() && rows.maxOf { it.size } == 1) {
            val numbered = Regex("^\\s*(\\d{1,4})\\s*[).\\-:]?\\s+(.{2,})$")
            val hits = rows.count { numbered.matches(it[0]) }
            rows = if (hits > rows.size * 0.6) {
                rows.map { r -> numbered.matchEntire(r[0])?.let { listOf(it.groupValues[1], it.groupValues[2].trim()) } ?: listOf("", r[0]) }
            } else {
                rows.map { r -> r[0].split(Regex("\\t|\\s{2,}")).map(String::trim).filter(String::isNotBlank).ifEmpty { r } }
            }
        }
        require(rows.isNotEmpty()) { "The document contains no text rows." }
        return rectangular(rows)
    }

    private fun rectangular(rows: List<List<String>>): List<List<String>> {
        if (rows.isEmpty()) return rows
        val width = rows.maxOf { it.size }
        return rows.map { row -> List(width) { i -> row.getOrElse(i) { "" } } }
    }

    private fun unzip(input: InputStream, keep: (String) -> Boolean): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var retainedBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && keep(entry.name)) {
                    val bytes = zip.readBytesLimited(MAX_ZIP_ENTRY_BYTES)
                    retainedBytes += bytes.size
                    require(retainedBytes <= MAX_ZIP_TOTAL_BYTES) { "Office file is too large to import safely." }
                    out[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        return out
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Roster file is too large to import safely." }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private const val MAX_TEXT_BYTES = 5 * 1024 * 1024
    private const val MAX_ZIP_ENTRY_BYTES = 10 * 1024 * 1024
    private const val MAX_ZIP_TOTAL_BYTES = 20L * 1024 * 1024

    private fun xml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        try { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) } catch (_: Exception) {}
        try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Exception) {}
        try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
        try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
        try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") } catch (_: Exception) {}
        try { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") } catch (_: Exception) {}
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun childElements(node: Node, localName: String): List<Element> = buildList {
        val list = node.childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName == localName || n.nodeName.substringAfter(':') == localName)) add(n as Element)
        }
    }

    private fun descendants(node: Node, localName: String): List<Element> = buildList {
        fun visit(n: Node) {
            if (n.nodeType == Node.ELEMENT_NODE && (n.localName == localName || n.nodeName.substringAfter(':') == localName)) add(n as Element)
            val children = n.childNodes
            for (i in 0 until children.length) visit(children.item(i))
        }
        visit(node)
    }

    private fun textOf(node: Node): String = descendants(node, "t").joinToString("") { it.textContent.orEmpty() }
        .replace(Regex("\\s+"), " ").trim()

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)) n = n * 26 + (ch - 'A' + 1)
        return (n - 1).coerceAtLeast(0)
    }
}
