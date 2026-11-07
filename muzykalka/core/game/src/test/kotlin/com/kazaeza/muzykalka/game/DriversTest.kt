package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.FretPosition
import com.kazaeza.muzykalka.music.Fretboard
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MelodyKind
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.NoteName
import com.kazaeza.muzykalka.music.ProgressionText
import com.kazaeza.muzykalka.music.Song
import com.kazaeza.muzykalka.music.Tuning
import com.kazaeza.muzykalka.music.Voice
import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.Melody
import com.kazaeza.muzykalka.srs.CardState
import com.kazaeza.muzykalka.srs.Fsrs
import com.kazaeza.muzykalka.srs.Rating
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TirDriverTest {
    private fun ctx(level: Int, seed: Int = 1) = DriverContext(Random(seed), level, emptyList())

    @Test
    fun `every level produces a sounding task in a key - drone or cadence`() {
        for (level in 1..7) {
            val driver = TirDriver(KeyPicker(Random(level)))
            repeat(30) { i ->
                val task = driver.nextTask(ctx(level, i))
                val audio = requireNotNull(task.prompt.audio)
                assertNotNull(task.prompt.key)
                if (TirDriver.usesDrone(level)) {
                    val drone = requireNotNull(task.drone) { "level $level is heard over the drone" }
                    assertEquals(task.prompt.key!!.tonic.pitchClass, drone.first().pitchClass)
                    assertTrue(audio.lengthBeats >= task.question.lengthBeats)
                    task.question.notes.forEach { assertEquals(Voice.TONE, it.voice, "one soft timbre over the drone") }
                } else {
                    assertNull(task.drone, "chords keep the cadence")
                    assertTrue(audio.lengthBeats > task.question.lengthBeats, "cadence precedes the question")
                }
                when (task.kind) {
                    TirKind.NOTE -> assertTrue(task.notes.single().degree in task.noteChoices || task.noteChoices.any { it sameSoundAs task.notes.single().degree })
                    TirKind.MELODY -> assertTrue(task.notes.size in 2..5)
                    TirKind.CHORD -> assertTrue(task.chords.single() in task.chordChoices)
                    TirKind.PROGRESSION -> {
                        assertEquals(4, task.chords.size)
                        assertEquals(task.chordChoices[0], task.chords[0])
                    }
                }
                task.question.notes.forEach { assertTrue(it.note!!.value in 36..90, "register ${it.note}") }
            }
        }
    }

    @Test
    fun `the drone key holds for a block, opens with the scale, then moves on`() {
        val driver = TirDriver(KeyPicker(Random(7)))
        val r = Random(3)
        val tasks = (0 until TirDriver.KEY_BLOCK * 6).map { driver.nextTask(DriverContext(r, 2, emptyList())) }
        tasks.chunked(TirDriver.KEY_BLOCK).forEach { block ->
            assertEquals(1, block.map { it.prompt.key }.toSet().size, "one key per block")
            assertEquals(1, block.map { it.drone }.toSet().size, "one drone per block")
            val first = block.first()
            assertTrue(first.prompt.audio!!.lengthBeats > first.question.lengthBeats, "new key starts with the scale")
            block.drop(1).forEach { assertEquals(it.question.lengthBeats, it.prompt.audio!!.lengthBeats, "same key: just the note") }
        }
        val keys = tasks.chunked(TirDriver.KEY_BLOCK).map { it.first().prompt.key }
        keys.zipWithNext().forEach { (a, b) -> assertTrue(a != b, "the key changes between blocks") }
    }

    @Test
    fun `changing the level starts a new drone block`() {
        val driver = TirDriver(KeyPicker(Random(8)))
        driver.nextTask(ctx(1))
        val switched = driver.nextTask(ctx(2, 2))
        assertTrue(switched.prompt.audio!!.lengthBeats > switched.question.lengthBeats, "the scale plays again")
        val chord = driver.nextTask(ctx(5, 3))
        assertNull(chord.drone)
    }

    @Test
    fun `drone sits low and spans the octave`() {
        val drone = Arranger().drone(Key(NoteName.parse("E"), Mode.MAJOR))
        assertEquals(2, drone.size)
        assertEquals(12, drone[1].value - drone[0].value)
        assertTrue(drone[0].value in 45..56, "drone root ${drone[0]}")
    }

    @Test
    fun `level 1 asks mostly 1, 3, 5 with guests from the scale and shows all seven keys`() {
        val driver = TirDriver(KeyPicker(Random(3)))
        val tasks = (0 until 400).map { driver.nextTask(ctx(1, it)) }
        val degrees = tasks.map { it.notes.single().degree }
        assertEquals(Degree.NATURALS.toSet(), degrees.toSet(), "guests come from the whole scale")
        val core = degrees.count { it in TirDriver.CORE }.toDouble() / degrees.size
        assertTrue(core in 0.65..0.85, "core share $core")
        tasks.forEach { assertEquals(Degree.NATURALS, it.noteChoices) }
        assertTrue(tasks.map { it.notes.single().octave }.toSet().size > 1, "register jumps from level 1")
    }

    @Test
    fun `questions are independent - repeats happen`() {
        val driver = TirDriver(KeyPicker(Random(4)))
        val r = Random(11)
        val seq = (0 until 300).map { driver.nextTask(DriverContext(r, 2, emptyList())).itemKey }
        assertTrue(seq.zipWithNext().any { (a, b) -> a == b }, "the same degree may come twice in a row")
    }

    @Test
    fun `don't know is an honest wrong answer with the correction`() {
        val driver = TirDriver(KeyPicker(Random(6)))
        val task = driver.nextTask(ctx(2))
        val e = driver.evaluate(task, Answer.Pass)
        assertFalse(e.correct)
        assertNotNull(e.correction)
        assertEquals("?", e.givenCode)
    }

    @Test
    fun `right answers get the resolution, wrong answers get the correction (rule 5)`() {
        val driver = TirDriver(KeyPicker(Random(5)))
        val task = driver.nextTask(ctx(2))
        val right = driver.evaluate(task, Answer.DegreeChoice(task.notes.single().degree))
        assertTrue(right.correct)
        assertNotNull(right.confirmation)
        val wrongDegree = task.noteChoices.first { !(it sameSoundAs task.notes.single().degree) }
        val wrong = driver.evaluate(task, Answer.DegreeChoice(wrongDegree))
        assertFalse(wrong.correct)
        assertNotNull(wrong.correction)
        assertEquals(task.notes.single().degree.toString(), wrong.expectedCode)
    }

    @Test
    fun `enharmonic answers count`() {
        val driver = TirDriver(KeyPicker(Random(9)))
        var task: TirTask
        do task = driver.nextTask(ctx(3, Random.nextInt())) while (task.notes.single().degree != Degree.SHARP_FOUR)
        assertTrue(driver.evaluate(task, Answer.DegreeChoice(Degree.FLAT_FIVE)).correct)
    }

    @Test
    fun `progressions are judged on chords 2 to 4`() {
        val driver = TirDriver(KeyPicker(Random(2)))
        val task = driver.nextTask(ctx(6))
        assertEquals(3, task.answerLength)
        assertTrue(driver.evaluate(task, Answer.ChordFormula(task.chords.drop(1))).correct)
        assertFalse(driver.evaluate(task, Answer.ChordFormula(task.chords.take(3))).correct || task.chords.drop(1) == task.chords.take(3))
    }

    @Test
    fun `weak items come up more often`() {
        val history = (1..20).map {
            AttemptRecord(Variant.TIR_CLASSIC, 2, "tir:L2:4", "4", "3", false, 3000, null, 0, null, 0)
        }
        val driver = TirDriver(KeyPicker(Random(4)))
        var fours = 0
        repeat(400) { i ->
            val task = driver.nextTask(DriverContext(Random(i), 2, history))
            if (task.notes.single().degree == Degree.FOUR) fours++
        }
        // Uniform would be ~57 of 400; the no-immediate-repeat rule caps a single item near 25 %.
        assertTrue(fours > 400 / 7 * 3 / 2, "4 asked $fours times")
    }
}

class HuntDriverTest {
    private val board = Fretboard(Tuning.STANDARD, 22)

    @Test
    fun `find accepts only octaves that exist on the string`() {
        val driver = HuntDriver(Variant.HUNT_FIND, board)
        repeat(50) { i ->
            val task = driver.nextTask(DriverContext(Random(i), 1, emptyList()))
            assertTrue(task.string in listOf(5, 6))
            task.acceptable.forEach { n ->
                val fret = n.value - board.tuning.open(task.string!!).value
                assertTrue(fret in 0..22)
                assertEquals(task.pitchClass, n.pitchClass)
            }
            val right = task.acceptable.first()
            assertTrue(driver.evaluate(task, Answer.NotePlayed(right, 3.0)).correct)
            val wrongOctave = right + 36
            assertFalse(driver.evaluate(task, Answer.NotePlayed(wrongOctave, 0.0)).correct)
        }
    }

    @Test
    fun `timer shrinks after right answers and has a floor`() {
        val driver = HuntDriver(Variant.HUNT_FIND, board)
        val ctx = DriverContext(Random(1), 1, emptyList())
        var limit = 0L
        repeat(60) {
            val task = driver.nextTask(ctx)
            limit = driver.timeLimitMs(task, ctx)!!
            driver.evaluate(task, Answer.NotePlayed(task.acceptable.first(), 0.0))
        }
        assertEquals(1_200L, limit)
    }

    @Test
    fun `level 4 measures degrees from the root actually played`() {
        val driver = HuntDriver(Variant.HUNT_FIND, board)
        val ctx = DriverContext(Random(7), 4, emptyList())
        val root = driver.nextTask(ctx)
        assertEquals(HuntKind.ROOT, root.kind)
        val played = root.acceptable.first()
        assertTrue(driver.evaluate(root, Answer.NotePlayed(played, 0.0)).correct)
        val degreeTask = driver.nextTask(ctx)
        assertEquals(HuntKind.DEGREE, degreeTask.kind)
        assertEquals(played.pitchClass + degreeTask.degree!!.semitones, degreeTask.pitchClass)
    }

    @Test
    fun `reverse hunt is answered by name, enharmonics equal`() {
        val driver = HuntDriver(Variant.HUNT_REVERSE, board)
        repeat(30) { i ->
            val task = driver.nextTask(DriverContext(Random(i), 1, emptyList()))
            assertEquals(HuntKind.REVERSE, task.kind)
            val pos = requireNotNull(task.position)
            assertEquals(board.noteAt(pos).pitchClass, task.pitchClass)
            val sharpName = HuntDriver.PAD[task.pitchClass.value]
            assertTrue(driver.evaluate(task, Answer.NoteNamed(sharpName)).correct)
        }
        assertEquals(FretPosition(6, 0).string, 6)
    }

    @Test
    fun `all octaves counts each found octave once`() {
        val driver = HuntDriver(Variant.HUNT_ALL_OCTAVES, board)
        val ctx = DriverContext(Random(3), 1, emptyList())
        val first = driver.nextTask(ctx)
        val note = first.acceptable.first()
        assertTrue(driver.evaluate(first, Answer.NotePlayed(note, 0.0)).correct)
        val second = driver.nextTask(ctx)
        assertEquals(first.pitchClass, second.pitchClass)
        assertFalse(note in second.acceptable)
        assertEquals(1, second.found.size)
    }

    @Test
    fun `timeouts are wrong and carry the right note`() {
        val driver = HuntDriver(Variant.HUNT_FIND, board)
        val task = driver.nextTask(DriverContext(Random(1), 2, emptyList()))
        val e = driver.evaluate(task, Answer.TimedOut)
        assertFalse(e.correct)
        assertNotNull(e.correction)
    }
}

class TaperSessionTest {
    private val song = Song(
        id = "al", title = "Autumn Leaves", mode = Mode.MINOR,
        sections = ProgressionText.parseSections("A: | iv7 | bVII7 | bIIImaj7 | bVImaj7 |"), form = listOf("A"),
    )
    private val riff = Melody("r", "Мой рифф", Mode.MINOR, MelodyKind.RIFF, DegreeNote.parseLine("1 b3 4 5 b7 1'"))

    private fun session(items: List<TaperItem>, hints: Boolean = true) =
        TaperSession(items, KeyPicker(Random(1)), Fsrs(), today = 100, random = Random(1), hintsAllowed = hints)

    @Test
    fun `a clean order without hints counts as playable now and schedules the song`() {
        val s = session(listOf(TaperItem("al", "Autumn Leaves", song = song, card = CardState())))
        assertTrue(s.next(0))
        s.onFirstNote(2_000)
        s.finishPlaying()
        val r = requireNotNull(s.grade(SelfGrade.CLEAN))
        assertTrue(r.playableNow)
        assertEquals(null, r.outcome.formulaAccuracy, "«Что это было?» is not asked since O-13")
        assertEquals(Rating.EASY, r.rating)
        assertTrue(r.newCard.dueDay!! > 100)
        assertEquals(TaperPhase.RESULT, s.phase)
        assertFalse(s.next(1_000))
        assertEquals(TaperPhase.FINISHED, s.phase)
    }

    @Test
    fun `hints climb one step at a time, stop at the degrees and cost the order`() {
        val s = session(listOf(TaperItem("al", "Autumn Leaves", song = song, card = CardState())))
        s.next(0)
        assertEquals(HintStep.NONE, s.hint)
        assertEquals(HintStep.FORM, s.requestHint())
        assertEquals(HintStep.FIRST_CHORD, s.requestHint())
        assertEquals(HintStep.DEGREES, s.requestHint())
        assertEquals(HintStep.DEGREES, s.requestHint(), "no letters above the degrees (O-13)")
        s.finishPlaying()
        val r = requireNotNull(s.grade(SelfGrade.CLEAN))
        assertEquals(Rating.AGAIN, r.rating)
        assertFalse(r.playableNow)
        assertEquals(101, r.newCard.dueDay) // forgotten → ordered tomorrow
    }

    @Test
    fun `exams forbid hints`() {
        val s = session(listOf(TaperItem("al", "Autumn Leaves", song = song, card = CardState())), hints = false)
        s.next(0)
        assertEquals(HintStep.NONE, s.requestHint())
    }

    @Test
    fun `riffs are graded by the self-grade alone`() {
        val s = session(listOf(TaperItem("r", "Мой рифф", melody = riff, card = CardState())))
        s.next(0)
        s.finishPlaying()
        val r = requireNotNull(s.grade(SelfGrade.STUMBLED))
        assertEquals(Rating.HARD, r.rating)
        assertEquals(TaperPhase.RESULT, s.phase)
    }

    @Test
    fun `an order starts where the vanishing cues say, but never in an exam`() {
        val item = TaperItem("al", "Autumn Leaves", song = song, card = CardState(), startHint = HintStep.DEGREES)
        val s = session(listOf(item))
        s.next(0)
        assertEquals(HintStep.DEGREES, s.hint)
        val exam = session(listOf(item), hints = false)
        exam.next(0)
        assertEquals(HintStep.NONE, exam.hint)
        val old = session(listOf(item.copy(startHint = HintStep.FULL_SHEET)))
        old.next(0)
        assertEquals(HintStep.DEGREES, old.hint, "letters are never shown")
    }

    @Test
    fun `vanishing cues - new songs open on the degrees, a clean play takes a step off`() {
        assertEquals(HintStep.DEGREES, TaperGrading.startHint(null, null))
        assertEquals(HintStep.FIRST_CHORD, TaperGrading.startHint(HintStep.DEGREES, SelfGrade.CLEAN))
        assertEquals(HintStep.FORM, TaperGrading.startHint(HintStep.FIRST_CHORD, SelfGrade.CLEAN))
        assertEquals(HintStep.NONE, TaperGrading.startHint(HintStep.FORM, SelfGrade.CLEAN))
        assertEquals(HintStep.NONE, TaperGrading.startHint(HintStep.NONE, SelfGrade.CLEAN))
        assertEquals(HintStep.FORM, TaperGrading.startHint(HintStep.FORM, SelfGrade.STUMBLED))
        assertEquals(HintStep.DEGREES, TaperGrading.startHint(HintStep.NONE, SelfGrade.FAILED))
        assertEquals(HintStep.FIRST_CHORD, TaperGrading.startHint(HintStep.FULL_SHEET, SelfGrade.CLEAN))
    }

    @Test
    fun `queue puts overdue items first and skips items not due`() {
        val overdue = TaperItem("a", "A", song = song, card = CardState(stability = 5.0, difficulty = 5.0, lastReviewDay = 80, dueDay = 90))
        val new = TaperItem("b", "B", song = song.copy(id = "b"), card = CardState())
        val future = TaperItem("c", "C", song = song.copy(id = "c"), card = CardState(stability = 30.0, difficulty = 5.0, lastReviewDay = 95, dueDay = 130))
        val due = TaperSession.due(listOf(future, new, overdue), today = 100, random = Random(1))
        assertEquals(listOf("a", "b"), due.map { it.id })
    }

    @Test
    fun `random key every order (rule 2)`() {
        val items = (1..6).map { TaperItem("s$it", "S$it", song = song.copy(id = "s$it"), card = CardState()) }
        val s = session(items)
        val keys = mutableListOf<String>()
        while (s.next(0)) {
            keys += s.key.toString()
            s.finishPlaying(); s.grade(SelfGrade.CLEAN)
        }
        assertEquals(6, keys.size)
        keys.windowed(2).forEach { (a, b) -> assertTrue(a != b) }
    }
}

class MetricsAndExamTest {
    private fun a(v: Variant, key: String, ok: Boolean, ms: Long, exp: String = "", given: String = "") =
        AttemptRecord(v, 1, key, exp, given, ok, ms, null, 0, null, 0)

    @Test
    fun `fret heat groups by string and pitch class`() {
        val cells = Metrics.fretHeat(listOf(a(Variant.HUNT_FIND, "hunt:s5:pc6", true, 800), a(Variant.HUNT_FIND, "hunt:s5:pc6", false, 5000)))
        assertEquals(1, cells.size)
        assertEquals(0.5, cells.single().accuracy)
        assertEquals(800L, cells.single().medianMs)
    }

    @Test
    fun `confusion matrix and worst pair`() {
        val list = List(5) { a(Variant.TIR_CLASSIC, "tir:L2:4", false, 1000, "4", "3") } + a(Variant.TIR_CLASSIC, "tir:L2:5", true, 900, "5", "5")
        val c = Metrics.confusion(list)
        assertEquals(5, c.counts[Metrics.CHROMATIC_LABELS.indexOf("4")][Metrics.CHROMATIC_LABELS.indexOf("3")])
        assertEquals("3" to "4", Metrics.worstPair(c))
    }

    @Test
    fun `playable now series follows the latest order per item`() {
        val series = Metrics.playableNowSeries(
            listOf(OrderFact("a", 1, true, 1), OrderFact("b", 1, false, 2), OrderFact("b", 2, true, 3), OrderFact("a", 3, false, 4)),
        )
        assertEquals(listOf(1 to 1, 2 to 2, 3 to 1), series)
    }

    @Test
    fun `link scores are null until trained`() {
        val scores = Metrics.linkScores(listOf(a(Variant.TIR_CLASSIC, "tir:L1:1", true, 1000)))
        assertNull(scores[Link.NAME_TO_PLACE])
        assertTrue(scores[Link.SOUND_TO_NAME]!! > 0.9)
    }

    @Test
    fun `streak counts back from today or yesterday`() {
        assertEquals(3, Metrics.streak(listOf(10, 9, 8, 5), today = 10))
        assertEquals(3, Metrics.streak(listOf(9, 8, 7), today = 10))
        assertEquals(0, Metrics.streak(listOf(7), today = 10))
        assertEquals(0, Metrics.streak(emptyList(), today = 10))
    }

    @Test
    fun `exam measures`() {
        val hunt = listOf(a(Variant.HUNT_FIND, "x", true, 800), a(Variant.HUNT_FIND, "x", false, 6000))
        assertEquals((800 + 6000) / 2 / 1000.0, Exam.measure(Skill.NECK_LOW_STRINGS, hunt), 1e-9)
        assertEquals(0.5, Exam.measure(Skill.EAR_DEGREES, hunt), 1e-9)
    }

    @Test
    fun `exam config is not time boxed but drills are`() {
        val config = RoundConfig.exam(20)
        assertEquals(20, config.taskLimit)
        val engine = RoundEngine(
            HuntDriver(Variant.HUNT_FIND, Fretboard(Tuning.STANDARD), adaptiveTimer = false), config, Random(1), level = 1,
        )
        engine.start(0)
        var now = 0L
        repeat(20) {
            val task = engine.state.task!!
            engine.answer(Answer.NotePlayed(task.acceptable.first(), 0.0), now + 500)
            now += 2_000
            engine.tick(now)
        }
        assertEquals(RoundPhase.FINISHED, engine.state.phase)
        assertEquals(20, engine.state.attempts.size)
        assertEquals(ChordDegree.parse("I"), ChordDegree.parse("I"))
        assertEquals(NoteName.parse("C"), NoteName.parse("C"))
        assertEquals(MidiNote(60), MidiNote(60))
    }
}

class StepsDriverTest {
    @Test
    fun `every level gives a progression in a random key and plays it after the answer`() {
        for (level in 1..StepsLadder.LEVELS.size) {
            val driver = StepsDriver(KeyPicker(Random(level)))
            val keys = mutableSetOf<String>()
            repeat(20) { i ->
                val task = driver.nextTask(DriverContext(Random(i), level, emptyList()))
                assertNull(task.prompt.audio, "the user plays first")
                assertTrue(task.pattern in StepsLadder.LEVELS[level - 1])
                keys += task.key.toString()
                val ok = driver.evaluate(task, Answer.SelfReport(SelfGrade.CLEAN))
                assertTrue(ok.correct)
                assertNotNull(ok.confirmation)
                val no = driver.evaluate(task, Answer.SelfReport(SelfGrade.FAILED))
                assertFalse(no.correct)
                assertNotNull(no.correction)
            }
            assertTrue(keys.size > 3, "keys vary (rule 2)")
        }
    }

    @Test
    fun `level 1 is the banal two-chord stuff`() {
        StepsLadder.LEVELS[0].forEach { p -> assertTrue(p.chords.size <= 3 && p.chords.all { it.root.number in setOf(1, 4, 5) }, p.code) }
    }

    @Test
    fun `failed moves come back more often`() {
        val driver = StepsDriver(KeyPicker(Random(1)))
        val pool = StepsLadder.LEVELS[0]
        val bad = "steps:L1:" + pool[0].chords.joinToString("-") { it.toString() }
        val history = pool.flatMap { p ->
            val key = "steps:L1:" + p.chords.joinToString("-") { it.toString() }
            List(10) { AttemptRecord(Variant.TAPER_STEPS, 1, key, p.code, "CLEAN", key != bad, 10_000, null, 0, null, 0) }
        }
        val r = Random(2)
        val picks = (0 until 600).map { driver.nextTask(DriverContext(r, 1, history)).itemKey }
        assertTrue(picks.count { it == bad } > 600 / pool.size * 2, "the failed move is picked far more often")
    }

    @Test
    fun `sets keep their length and give the songs' time to the moves when nothing is due`() {
        assertEquals(20.0, DailySet.DEFAULT.sumOf { it.minutes })
        assertEquals(5.0, DailySet.MINIMAL.sumOf { it.minutes })
        listOf(DailySet.DEFAULT, DailySet.MINIMAL).forEach { plan ->
            val songless = DailySet.withoutSongs(plan)
            assertEquals(plan.sumOf { it.minutes }, songless.sumOf { it.minutes })
            assertTrue(songless.none { it.variant == Variant.TAPER_ORDER })
            assertEquals(1, songless.count { it.variant == Variant.TAPER_STEPS })
        }
        val noSteps = listOf(SetSegment(Variant.TIR_CLASSIC, 1.0), SetSegment(Variant.TAPER_ORDER, 4.0))
        assertEquals(listOf(SetSegment(Variant.TIR_CLASSIC, 1.0), SetSegment(Variant.TAPER_STEPS, 4.0)), DailySet.withoutSongs(noSteps))
    }
}

class MoreMetricsTest {
    private fun tir(expected: String, given: String, at: Long, correct: Boolean = expected == given) =
        AttemptRecord(Variant.TIR_CLASSIC, 2, "tir:L2:$expected", expected, given, correct, 1500, null, 0, null, at)

    @Test
    fun `guessing detects answers that echo the previous question`() {
        val a = listOf(
            tir("3", "3", 0), tir("5", "3", 5_000), tir("6", "5", 10_000), tir("1", "?", 15_000, correct = false), tir("2", "4", 20_000),
        )
        val g = Metrics.guessing(a)
        assertEquals(3, g.wrong)
        assertEquals(2, g.echoes)
        assertEquals(1, g.passes)
    }

    @Test
    fun `degree stats and longest streak`() {
        val a = listOf(tir("3", "3", 0), tir("3", "5", 1), tir("1", "1", 2))
        val stats = Metrics.degreeStats(a)
        assertEquals(listOf("1", "3"), stats.map { it.code })
        assertEquals(0.5, stats[1].accuracy, 1e-9)
        assertEquals(3, Metrics.longestStreak(listOf(1, 2, 3, 7, 8)))
        assertEquals(0, Metrics.longestStreak(emptyList()))
    }

    @Test
    fun `parts of the day`() {
        val a = listOf(tir("1", "1", 9), tir("1", "2", 20))
        val parts = Metrics.byPartOfDay(a) { it.toInt() }
        assertEquals(1, parts.first { it.ru == "утро" }.attempts)
        assertEquals(0.0, parts.first { it.ru == "вечер" }.accuracy!!, 1e-9)
        assertNull(parts.first { it.ru == "ночь" }.accuracy)
    }
}
