package com.kazaeza.muzykalka.srs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class FsrsGoldenTest {

    private val golden = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fsrs6-golden.json")) { "fixture missing" }.readText(),
    ).jsonObject

    @Test
    fun `default parameters match the fixture`() {
        val expected = golden.getValue("parameters").jsonArray.map { it.jsonPrimitive.double }
        assertEquals(expected, FsrsParameters.DEFAULT.w.toList())
    }

    @Test
    fun `every golden case reproduces py-fsrs exactly`() {
        val cases = golden.getValue("cases").jsonArray
        assertTrue(cases.size >= 50)
        for (case in cases) {
            val c = case.jsonObject
            val name = c.getValue("name").jsonPrimitive.content
            val fsrs = Fsrs(desiredRetention = c.getValue("desiredRetention").jsonPrimitive.double, maxIntervalAfterAgainDays = null)
            var card = CardState()
            c.getValue("steps").jsonArray.forEachIndexed { i, stepEl ->
                val step = stepEl.jsonObject
                val day = step.getValue("day").jsonPrimitive.int
                val rBefore = step.getValue("retrievabilityBefore")
                if (rBefore !is JsonNull) {
                    assertEquals(rBefore.jsonPrimitive.double, fsrs.retrievability(card, day), 1e-9, "$name#$i R")
                }
                card = fsrs.review(card, Rating.entries.first { it.value == step.getValue("rating").jsonPrimitive.int }, day)
                assertEquals(step.getValue("stability").jsonPrimitive.double, card.stability!!, 1e-9, "$name#$i S")
                assertEquals(step.getValue("difficulty").jsonPrimitive.double, card.difficulty!!, 1e-9, "$name#$i D")
                assertEquals(step.getValue("dueDay").jsonPrimitive.int, card.dueDay, "$name#$i due")
            }
        }
    }

    @Test
    fun `lapses and reps are counted`() {
        val fsrs = Fsrs()
        var card = CardState()
        card = fsrs.review(card, Rating.AGAIN, 0) // first review: not a lapse
        card = fsrs.review(card, Rating.GOOD, card.dueDay!!)
        card = fsrs.review(card, Rating.AGAIN, card.dueDay!!)
        assertEquals(3, card.reps)
        assertEquals(1, card.lapses)
    }

    @Test
    fun `a forgotten song is due again tomorrow`() {
        val fsrs = Fsrs()
        var card = CardState()
        repeat(4) { card = fsrs.review(card, Rating.GOOD, card.dueDay ?: 0) }
        val day = card.dueDay!!
        card = fsrs.review(card, Rating.AGAIN, day)
        assertEquals(day + 1, card.dueDay)
    }

    @Test
    fun `study day rolls over at 4 am local time`() {
        val sd = StudyDay(ZoneId.of("Europe/Moscow"), dayStartHour = 4)
        val lateNight = sd.of(Instant.parse("2026-11-06T22:30:00Z")) // 01:30 MSK on Nov 7
        val evening = sd.of(Instant.parse("2026-11-06T18:00:00Z")) // 21:00 MSK on Nov 6
        val morning = sd.of(Instant.parse("2026-11-07T02:00:00Z")) // 05:00 MSK on Nov 7
        assertEquals(evening, lateNight)
        assertEquals(evening + 1, morning)
    }
}
