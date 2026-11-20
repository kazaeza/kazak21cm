package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.ChordQuality
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.PitchClass
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.ScoreNote
import com.kazaeza.muzykalka.music.Voice

/**
 * One scene of «Бас-линия»: only the bass of a [loop] sounds ([question], low, in two octaves so that a phone speaker has it)
 * and it is to be played on the guitar, a note for each chord, in order, any octave. [heard] is the whole band once: what the
 * bass was the bottom of. No drone — it would leak into the microphone (ADR-021).
 */
data class BassLineTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val loop: List<ChordDegree>,
    /** The pitch class each played note is to have, one per chord. */
    val pitchClasses: List<PitchClass>,
    /** 1-based place in the cycle of [scenes] scenes. */
    val scene: Int,
    val scenes: Int,
    val question: Score,
    val heard: Score,
    override val intro: Score? = null,
) : DroneTask {
    override val drone: List<MidiNote>? get() = null
    override val replay: Score get() = question

    /** How many notes the line has. */
    val expected: Int get() = loop.size

    /** The degrees of the bass notes in the key — «1 4 5 1». */
    val degrees: List<Degree> get() = pitchClasses.map { degreeOf(key, it) }

    companion object {
        fun degreeOf(key: Key, pc: PitchClass): Degree {
            val semitones = pc - key.tonic.pitchClass
            return key.mode.diatonicSpelling(semitones) ?: Degree.canonical(semitones)
        }
    }
}

/**
 * O-77 «Бас-линия» (ADR-075, idea 5 of the research notes — Ted Greene's cycle): the bass first. A loop of 3–4 chords is
 * heard as its bass alone and the bass is played on the guitar — a note per chord, in order, in any octave, at the user's own
 * pace (the microphone is asked for the pitch class of each note, not for rhythm). Then the whole band plays the same loop,
 * so that the bass line is heard as the bottom of something; the bass again, now remembered; and the same loop in another
 * key — one cycle of three scenes, [SCENES]. Bass-first is what Greene, the Nashville players and Musical U teach; that it
 * works better than the top first is not shown anywhere, and phones lose the bass, hence two octaves (and headphones help).
 * Level 1: I IV V; 2: + vi; 3: + ♭VII and minor loops.
 */
class BassLineDriver(
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<BassLineTask> {
    override val variant: Variant = Variant.TIR_BASSLINE

    override val accepts: Set<Class<out Answer>> = setOf(Answer.Phrase::class.java, Answer.Pass::class.java)

    private class Cycle(val loop: List<ChordDegree>, val keys: List<Key>)

    private var cycle: Cycle? = null
    private var scene = 0
    private var lastLoop: List<ChordDegree>? = null

    override fun nextTask(context: DriverContext): BassLineTask {
        val level = context.level.coerceIn(1, Levels.TIR_BASSLINE.size)
        var current = cycle
        if (current == null || scene >= SCENES) {
            current = newCycle(context, level)
            cycle = current
            scene = 0
        }
        // scenes 1 and 2 are in the first key, scene 3 in the second; a key is set by its scale when it first sounds
        val key = current.keys[if (scene < 2) 0 else 1]
        val keyChanges = scene == 0 || scene == 2
        scene++
        val loop = current.loop
        val pcs = loop.map { key.pitchClassOf(it.bass ?: it.root) }
        val question = bassLine(loop, key)
        val prefix = "bassline:L$level:"
        return BassLineTask(
            itemKey = prefix + "s$scene:" + loop.joinToString("-") { ChordParser.formatRoman(it) },
            prompt = Prompt(title = "?", audio = question, key = key),
            level = level, key = key, loop = loop, pitchClasses = pcs, scene = scene, scenes = SCENES,
            question = question,
            heard = arranger.groove(loop, key, beatsEach = BAND_BEATS, tempoBpm = BAND_TEMPO),
            intro = if (keyChanges) orientation(key) else null,
        )
    }

    private fun newCycle(context: DriverContext, level: Int): Cycle {
        val r = context.random
        val options = loops(level)
        val loop = ItemWeights.pick(r, options, avoid = lastLoop) { l ->
            ItemWeights.weight("bassline:L$level:s1:" + l.joinToString("-") { ChordParser.formatRoman(it) }, context.history)
        }
        lastLoop = loop
        val minor = loop.first().quality == ChordQuality.MINOR
        val mode = if (minor) Mode.MINOR else Mode.MAJOR
        val first = keyPicker.next(mode)
        var second = keyPicker.next(mode)
        var guard = 0
        while (second.tonic.pitchClass == first.tonic.pitchClass && guard++ < 6) second = keyPicker.next(mode)
        return Cycle(loop, listOf(first, second))
    }

    /** The bass of the loop alone: a note per chord, low, with its octave above it softer. */
    private fun bassLine(loop: List<ChordDegree>, key: Key): Score {
        val notes = ArrayList<ScoreNote>()
        loop.forEachIndexed { i, chord ->
            val low = arranger.bassNote(chord, key) + 12
            notes += ScoreNote(i * NOTE_BEATS, NOTE_BEATS * 0.9, Voice.BASS, low, 0.9f)
            notes += ScoreNote(i * NOTE_BEATS, NOTE_BEATS * 0.9, Voice.BASS, low + 12, 0.55f)
        }
        return Score(BASS_TEMPO, notes)
    }

    private fun orientation(key: Key): Score {
        val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
        return arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.4, tempoBpm = BASS_TEMPO, tonicFloor = key.tonicAtOrAbove(MidiNote(52)))
    }

    private fun spell(key: Key, notes: List<PitchClass>): Pair<String, String> {
        val degrees = notes.map { BassLineTask.degreeOf(key, it) }
        return degrees.joinToString(" ") { DegreeFormatter.degree(it, notation) } to degrees.joinToString(" ") { it.toString() }
    }

    override fun evaluate(task: BassLineTask, answer: Answer): Evaluation {
        val (expectedText, expectedCode) = spell(task.key, task.pitchClasses)
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.Phrase -> {
                val played = answer.notes.map { it.note.pitchClass }
                val (text, code) = if (played.isEmpty()) "—" to "—" else spell(task.key, played)
                Triple(played == task.pitchClasses, text, code)
            }
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        return Evaluation(
            correct = correct,
            expectedText = expectedText,
            givenText = givenText,
            correction = if (correct) null else task.question.then(task.heard, gapBeats = 0.8),
            taskHadSound = true,
            confirmation = if (correct) task.heard else null,
            expectedCode = expectedCode,
            givenCode = givenCode,
        )
    }

    companion object {
        const val SCENES = 3
        const val BASS_TEMPO = 70.0
        const val NOTE_BEATS = 1.5
        const val BAND_TEMPO = 100.0
        const val BAND_BEATS = 2.0

        /** The loops asked on [level]: the chords whose bass is played, the first is where the loop opens. */
        fun loops(level: Int): List<List<ChordDegree>> {
            fun l(spec: String) = spec.split(' ').map(ChordDegree::parse)
            val one = listOf(l("I IV V I"), l("I V IV I"), l("I IV I V"))
            val two = one + listOf(l("I vi IV V"), l("vi IV I V"), l("I V vi IV"))
            val three = two + listOf(l("I ♭VII IV I"), l("I ♭VII ♭VI ♭VII"), l("i ♭VII ♭VI ♭VII"), l("i iv ♭VII i"))
            return when {
                level <= 1 -> one
                level == 2 -> two
                else -> three
            }
        }

        /** Finding a bass note on the neck takes a while: the median that opens the next level. */
        const val PROMOTION_TARGET_MS = 7_000L
    }
}
