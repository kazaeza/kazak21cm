package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-74 (ADR-071): the tag of what was chosen, in the words of the right answers. */
class ContrastTest {
    @Test
    fun `a degree is told by its feel and where it leans`() {
        assertEquals("2 — вопрос: тянет вниз к 1", Contrasts.degree(Degree.parse("2"), Mode.MAJOR, DegreeNotation.ARABIC))
        assertTrue(Contrasts.degree(Degree.parse("7"), Mode.MAJOR, DegreeNotation.ARABIC).contains("вверх"), "the leading tone leans up")
        assertTrue(Contrasts.degree(Degree.parse("1"), Mode.MAJOR, DegreeNotation.ARABIC).contains("уже дома"))
        assertTrue(Contrasts.degree(Degree.parse("6"), Mode.MAJOR, DegreeNotation.SOLFEGE).startsWith("ля"), "in the user's notation")
    }

    @Test
    fun `a chord is told by its job and where it pulls`() {
        val v = Contrasts.chord(ChordParser.parse("V"), Mode.MAJOR, DegreeNotation.ROMAN)
        assertTrue(v.startsWith("V — Напряжение") && v.contains("тянется в I"), v)
        val one = Contrasts.chord(ChordParser.parse("I"), Mode.MAJOR, DegreeNotation.ROMAN)
        assertTrue(one.contains("Дом") && one.contains("никуда не тянет"), one)
    }

    @Test
    fun `over a chord it says what the note does against that chord`() {
        val t = Contrasts.degreeOver(Degree.parse("4"), ChordParser.parse("I"), Mode.MAJOR, DegreeNotation.ARABIC)
        assertTrue(t.startsWith("4 — ") && t.contains("над I"), t)
    }
}
