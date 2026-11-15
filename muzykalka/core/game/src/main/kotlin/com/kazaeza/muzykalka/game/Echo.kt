package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.PitchClass
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice

/**
 * One question of «Попугай»: the tonic of [key] sounds, then [note]; the note is to be played on the guitar — any
 * octave of [pitchClass] counts. No drone: it would leak into the microphone (ADR-021).
 */
data class EchoTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val tonic: MidiNote,
    val note: DegreeNote,
    val pitchClass: PitchClass,
    /** The tonic and the note: what «once more» plays. */
    val question: Score,
) : DroneTask {
    override val drone: List<MidiNote>? get() = null
    override val replay: Score get() = question
}

/**
 * PR-01 «Попугай», step 1 (ADR-051): sound → place, the link nothing trained before. The key is named and set by its
 * scale (a block of [KEY_BLOCK] questions); every question plays the tonic for reference and then a degree, and the
 * user plays that sound on the guitar. The first note played is the answer (rule 4), any octave; then the degree is
 * named, walks home, and the screen shows where it lies on the neck. Phrases of 2–6 notes are the next steps of PR-01.
 */
class EchoDriver(
    private val keyPicker: KeyPicker,
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<EchoTask> {
    override val variant: Variant = Variant.PARROT_ECHO

    override val accepts: Set<Class<out Answer>> = setOf(Answer.NotePlayed::class.java, Answer.Pass::class.java)

    private var blockKey: Key? = null
    private var blockLevel = 0
    private var blockLeft = 0

    override fun nextTask(context: DriverContext): EchoTask {
        val level = context.level.coerceIn(1, Levels.ECHO.size)
        val r = context.random
        val kept = blockKey?.takeIf { blockLeft > 0 && blockLevel == level }
        val key = kept ?: keyPicker.next(if (level >= 2 && r.nextDouble() < TirDriver.MINOR_SHARE) Mode.MINOR else Mode.MAJOR)
        if (kept == null) {
            blockKey = key
            blockLevel = level
            blockLeft = KEY_BLOCK
        }
        blockLeft--
        val pool = if (level == 1) listOf(key.mode.degrees[0], key.mode.degrees[2], key.mode.degrees[4]) else key.mode.degrees
        val prefix = "echo:L$level:"
        val degree = ItemWeights.pick(r, pool) { ItemWeights.weight(prefix + it, context.history) }
        // the guitar's own register: the tonic from E3 up, the note in the octave above it or the one below
        val tonic = key.tonicAtOrAbove(MidiNote(52))
        val octaves = listOf(-1, 0).filter { (tonic.value + degree.semitones + 12 * it) in 45..76 }
        val note = DegreeNote(degree, octaves[r.nextInt(octaves.size)])
        val reference = arranger.line(listOf(DegreeNote(Degree.ONE)), key, Voice.TONE, beatsEach = 1.0, tempoBpm = TEMPO, tonicFloor = tonic)
        val question = reference.then(arranger.line(listOf(note), key, Voice.TONE, beatsEach = 2.0, tempoBpm = TEMPO, tonicFloor = tonic), gapBeats = 0.5)
        val scale = if (kept == null) {
            val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
            arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.4, tempoBpm = TEMPO, tonicFloor = tonic)
        } else {
            null
        }
        return EchoTask(
            itemKey = prefix + degree,
            prompt = Prompt(title = "?", audio = scale?.then(question, gapBeats = 1.0) ?: question, key = key),
            level = level, key = key, tonic = tonic, note = note, pitchClass = key.pitchClassOf(degree), question = question,
        )
    }

    /** The degree of [key] that [note] is, in the user's notation — «4», «♭7». */
    private fun heardAs(note: MidiNote, key: Key): Pair<String, String> {
        val semitones = (note.pitchClass - key.tonic.pitchClass)
        val degree = key.mode.diatonicSpelling(semitones) ?: Degree.canonical(semitones)
        return DegreeFormatter.degree(degree, notation) to degree.toString()
    }

    override fun evaluate(task: EchoTask, answer: Answer): Evaluation {
        val result = when (answer) {
            is Answer.NotePlayed -> {
                val (text, code) = heardAs(answer.note, task.key)
                Triple(answer.note.pitchClass == task.pitchClass, text, code) to answer.cents
            }
            Answer.Pass -> Triple(false, "не знаю", "?") to null
            else -> Triple(false, "—", "—") to null
        }
        val (triple, cents) = result
        val (correct, givenText, givenCode) = triple
        val home = arranger.line(listOf(task.note) + Harmony.resolution(task.note, task.key.mode), task.key, Voice.TONE, beatsEach = 0.6, tempoBpm = TEMPO, tonicFloor = task.tonic)
        return Evaluation(
            correct = correct,
            expectedText = DegreeFormatter.degree(task.note.degree, notation),
            givenText = givenText,
            correction = if (correct) null else home,
            centsError = cents,
            taskHadSound = true,
            confirmation = if (correct) home else null,
            expectedCode = task.note.degree.toString(),
            givenCode = givenCode,
        )
    }

    companion object {
        const val TEMPO = 110.0
        const val KEY_BLOCK = 8

        /** Finding a sound on the neck takes longer than naming it: the median that opens the next level. */
        const val PROMOTION_TARGET_MS = 5_000L
    }
}
