package com.gasczoology.varugai.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
private val displayShortFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)

fun displayDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(displayDateFormatter) }.getOrDefault(isoDate)

fun displayShortDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(displayShortFormatter) }.getOrDefault(isoDate)

fun displayWeekday(isoDate: String): String =
    runCatching {
        LocalDate.parse(isoDate).dayOfWeek.name.lowercase(Locale.ROOT)
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
    }.getOrDefault("")
