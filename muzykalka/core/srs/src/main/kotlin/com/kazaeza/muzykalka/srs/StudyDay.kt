package com.kazaeza.muzykalka.srs

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Converts wall-clock time to study-day numbers. A study day starts at [dayStartHour] local time
 * (default 04:00, like Anki), so practising at 01:00 still counts for "yesterday" — the streak and
 * the schedule follow the user's sleep, not midnight.
 */
class StudyDay(private val zone: ZoneId, private val dayStartHour: Int = 4) {
    init {
        require(dayStartHour in 0..23) { "dayStartHour must be 0..23" }
    }

    /** Day number (days since 1970-01-01 in the shifted local calendar). */
    fun of(instant: Instant): Int =
        instant.atZone(zone).minusHours(dayStartHour.toLong()).toLocalDate().toEpochDay().toInt()

    fun date(day: Int): LocalDate = LocalDate.ofEpochDay(day.toLong())
}
