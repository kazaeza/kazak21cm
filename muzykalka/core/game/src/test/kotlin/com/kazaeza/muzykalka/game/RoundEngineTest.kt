package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.srs.Rating
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

/** A trivial driver: "which degree is it?" with a sounding prompt. */
private class FakeTask(val degree: Degree, override val itemKey: String, override val prompt: Prompt) : Task

private class FakeDriver(private val withAudio: Boolean = true, private val limitMs: Long? = null) : ModeDriver<FakeTask> {
    override val variant = Variant.TIR_CLASSIC
    override val accepts = setOf(Answer.DegreeChoice::class.java)
    private val beep = Score(60.0, listOf(ScoreNote(0.0, 1.0, Voice.CLICK)))
    override fun nextTask(context: DriverContext): FakeTask {
        val d = Degree.NATURALS[context.random.nextInt(7)]
        return FakeTask(d, "fake:$d", Prompt("?", audio = if (withAudio) beep else null))
    }
    override fun evaluate(task: FakeTask, answer: Answer): Evaluation {
        val given = (answer as? Answer.DegreeChoice)?.degree
        val ok = given == task.degree
        return Evaluation(ok, task.degree.toString(), given?.toString() ?: "—", correction = if (ok) null else beep, taskHadSound = withAudio)
    }
    override fun timeLimitMs(task: FakeTask, context: DriverContext) = limitMs
}

class RoundEngineTest {

    private fun engine(driver: FakeDriver = FakeDriver(), lives: Int? = 3) =
        RoundEngine(driver, RoundConfig(durationMs = 60_000, lives = lives), Random(42), level = 1)

    @Test
    fun `answers during the prompt are ignored, only the first answer counts (rule 4)`() {
        val e = engine()
        var s = e.start(0)
        assertEquals(RoundPhase.PROMPTING, s.phase)
        s = e.answer(Answer.DegreeChoice(Degree.ONE), 100)
        assertEquals(RoundPhase.PROMPTING, s.phase)
        assertTrue(s.attempts.isEmpty())
        e.promptFinished(1_000)
        val right = (e.state.task as FakeTask).degree
        s = e.answer(Answer.DegreeChoice(right), 1_400)
        assertEquals(RoundPhase.FEEDBACK, s.phase)
        s = e.answer(Answer.DegreeChoice(Degree.FLAT_SEVEN), 1_500) // second answer: dropped
        assertEquals(1, s.attempts.size)
        assertTrue(s.attempts.single().correct)
        assertEquals(400, s.attempts.single().latencyMs) // measured from the end of the prompt
    }

    @Test
    fun `wrong answer carries a correction, costs a life and resets the combo (rule 5)`() {
        val e = engine()
        e.start(0); e.promptFinished(0)
        val wrong = Degree.NATURALS.first { it != (e.state.task as FakeTask).degree }
        val s = e.answer(Answer.DegreeChoice(wrong), 500)
        assertTrue(!s.lastEvaluation!!.correct)
        assertTrue(s.lastEvaluation.correction != null)
        assertEquals(2, s.livesLeft)
        assertEquals(0, s.streak)
    }

    @Test
    fun `evaluation without correction for a sounding task is impossible`() {
        assertThrows<IllegalArgumentException> { Evaluation(false, "♭3", "3", correction = null, taskHadSound = true) }
        assertThrows<IllegalArgumentException> { Evaluation(false, "", "3") }
    }

    @Test
    fun `round length is limited to 60-90 s (rule 6)`() {
        assertThrows<IllegalArgumentException> { RoundConfig(durationMs = 30_000) }
        assertThrows<IllegalArgumentException> { RoundConfig(durationMs = 120_000) }
    }

    @Test
    fun `round finishes when time is up, after the last feedback`() {
        val e = engine(lives = null)
        e.start(0); e.promptFinished(0)
        e.answer(Answer.DegreeChoice((e.state.task as FakeTask).degree), 59_900)
        assertEquals(RoundPhase.FEEDBACK, e.tick(60_100).phase)
        assertEquals(RoundPhase.FINISHED, e.tick(61_000).phase)
    }

    @Test
    fun `losing all lives ends the round`() {
        val e = engine(lives = 1)
        e.start(0); e.promptFinished(0)
        val wrong = Degree.NATURALS.first { it != (e.state.task as FakeTask).degree }
        e.answer(Answer.DegreeChoice(wrong), 100)
        assertEquals(RoundPhase.FINISHED, e.tick(2_000).phase)
    }

    @Test
    fun `combo multiplier grows every 5 correct answers`() {
        val e = engine(FakeDriver(withAudio = false), lives = null)
        var now = 0L
        e.start(now)
        repeat(10) {
            e.answer(Answer.DegreeChoice((e.state.task as FakeTask).degree), now + 300)
            now += 2_000
            e.tick(now)
        }
        assertEquals(10, e.state.streak)
        assertEquals(3, e.state.multiplier)
        assertEquals(10, e.state.attempts.size) // rule 8: one record per judged answer
    }

    @Test
    fun `per-task deadline produces a timed-out wrong answer`() {
        val e = engine(FakeDriver(withAudio = false, limitMs = 1_500))
        e.start(0)
        val s = e.tick(1_600)
        assertEquals(RoundPhase.FEEDBACK, s.phase)
        assertTrue(!s.attempts.single().correct)
    }

    @Test
    fun `unsupported answer types are ignored`() {
        val e = engine(FakeDriver(withAudio = false))
        e.start(0)
        val s = e.answer(Answer.Formula(listOf(DegreeNote(Degree.ONE))), 100)
        assertEquals(RoundPhase.LISTENING, s.phase)
        assertNull(s.lastEvaluation)
    }
}

class TaperGradingTest {
    private fun outcome(hint: HintStep = HintStep.NONE, self: SelfGrade = SelfGrade.CLEAN, formula: Double? = 1.0, start: Long? = 5_000) =
        OrderOutcome("s", hint, self, formula, start)

    @Test
    fun `ratings`() {
        assertEquals(Rating.GOOD, TaperGrading.rating(outcome()))
        assertEquals(Rating.EASY, TaperGrading.rating(outcome(start = 2_000)))
        assertEquals(Rating.HARD, TaperGrading.rating(outcome(hint = HintStep.FORM)))
        assertEquals(Rating.HARD, TaperGrading.rating(outcome(self = SelfGrade.STUMBLED)))
        assertEquals(Rating.HARD, TaperGrading.rating(outcome(formula = 0.8)))
        assertEquals(Rating.AGAIN, TaperGrading.rating(outcome(hint = HintStep.DEGREES)))
        assertEquals(Rating.AGAIN, TaperGrading.rating(outcome(self = SelfGrade.FAILED)))
        assertEquals(Rating.AGAIN, TaperGrading.rating(outcome(formula = 0.5)))
    }

    @Test
    fun `playable now requires no hints and a passed name check`() {
        assertTrue(TaperGrading.playableNow(outcome()))
        assertTrue(TaperGrading.playableNow(outcome(self = SelfGrade.STUMBLED)))
        assertTrue(!TaperGrading.playableNow(outcome(hint = HintStep.FORM)))
        assertTrue(!TaperGrading.playableNow(outcome(formula = 0.85)))
        assertTrue(TaperGrading.playableNow(outcome(formula = null)), "not asked = no name check (O-13)")
        assertTrue(!TaperGrading.playableNow(outcome(hint = HintStep.FORM, formula = null)))
    }

    @Test
    fun `tips shrink with every hint step`() {
        val tips = HintStep.entries.map { TaperGrading.tips(outcome(hint = it)) }
        assertEquals(tips.sortedDescending(), tips)
        assertEquals(0, TaperGrading.tips(outcome(self = SelfGrade.FAILED)))
    }

    @Test
    fun `formula accuracy`() {
        val e = listOf("iv7", "bVII7", "bIIImaj7", "bVImaj7").map(ChordDegree::parse)
        assertEquals(1.0, TaperGrading.formulaAccuracy(e, e))
        val qualityOff = listOf("iv7", "bVII", "bIIImaj7", "bVImaj7").map(ChordDegree::parse)
        assertEquals(0.875, TaperGrading.formulaAccuracy(e, qualityOff), 1e-9)
        val missing = e.dropLast(1)
        assertEquals(0.75, TaperGrading.formulaAccuracy(e, missing), 1e-9)
        assertEquals(0.0, TaperGrading.formulaAccuracy(e, emptyList()))
    }

    @Test
    fun `expected formula collapses repeats and lists each section once`() {
        val song = Song(
            id = "al", title = "Autumn Leaves", mode = Mode.MINOR,
            sections = ProgressionText.parseSections("A: | iv7 | bVII7 | i | i |\nB: | iiø7 | V7 | i |"),
            form = listOf("A", "A", "B"),
        )
        assertEquals("iv⁷ ♭VII⁷ i iiø⁷ V⁷ i", TaperGrading.expectedFormula(song).joinToString(" "))
    }
}
