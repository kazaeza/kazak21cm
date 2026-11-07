package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** O-09: anchors are the user's own, in degrees, and must lean on their degree. */
class AnchorTest {
    @Test
    fun `a typed opening in degrees becomes an anchor`() {
        val a = DegreeAnchor.parse(Degree.FIVE, "  Моя песня ", "5 5 6 5 1' 7").getOrThrow()
        assertEquals("Моя песня", a.title)
        assertEquals("5 5 6 5 1' 7", a.melodyText)
        assertEquals(DegreeNote(Degree.ONE, 1), a.melody[4])
        assertEquals("♭3 2 1", DegreeAnchor.parse(Degree.FLAT_THREE, "Минор", "b3 2 1").getOrThrow().melodyText)
    }

    @Test
    fun `wrong input says why in Russian`() {
        fun reason(title: String, melody: String) = DegreeAnchor.parse(Degree.FIVE, title, melody).exceptionOrNull()?.message ?: ""
        assertTrue(reason("", "5").startsWith("Напиши"))
        assertTrue(reason("Песня", "5 x 7").startsWith("Не разобрал"))
        assertTrue(reason("Песня", "3 2 1").contains("нет ступени 5"))
        assertTrue(reason("Песня", List(17) { "5" }.joinToString(" ")).contains("не больше"))
        assertTrue(reason("Песня", "  ").startsWith("Запиши"))
    }
}
