package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyPicker
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.ModeVibes
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice

/**
 * One question of «Какой лад?»: the tonic hums ([drone]) and the scale of [mode] runs over it ([question]). The key's
 * name would give the mode away, so it is not shown — only the tonic drone is the address.
 */
data class ModeQuizTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val mode: Mode,
    val choices: List<Mode>,
    val question: Score,
    override val drone: List<MidiNote>,
    /** The degree that gives the mode away, walking home — heard after the answer. */
    val mark: Score,
) : DroneTask {
    override val replay: Score get() = question
}

/**
 * O-51 «Какой лад?» (ADR-049): the colour of a whole scale. Over the tonic drone the scale of a mode runs up to the
 * octave and back (or down first); the mode is picked. The ladder adds the modes that differ from major and minor by
 * one degree — the Dorian's sixth, the Mixolydian's seventh, the Phrygian's second, the Lydian's fourth, the harmonic
 * minor's seventh. After the answer the degree that gives the mode away is heard walking home.
 */
class ModeQuizDriver(
    private val keyPicker: KeyPicker,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<ModeQuizTask> {
    override val variant: Variant = Variant.TIR_MODE

    override val accepts: Set<Class<out Answer>> = setOf(Answer.ModeChoice::class.java, Answer.Pass::class.java)

    private var last: Mode? = null

    override fun nextTask(context: DriverContext): ModeQuizTask {
        val level = context.level.coerceIn(1, Levels.TIR_MODE.size)
        val pad = pad(level)
        val r = context.random
        val prefix = "mode:L$level:"
        val mode = ItemWeights.pick(r, pad, avoid = last.takeIf { pad.size > 2 }) { ItemWeights.weight(prefix + it.name, context.history) }
        last = mode
        val key = keyPicker.next(mode)
        val tonic = key.tonicAtOrAbove(MidiNote(55))
        val up = mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
        // up and back, or down from the octave and up again: the same seven sounds, not the same tune every time
        val run = if (r.nextBoolean()) up + up.dropLast(1).reversed() else up.reversed() + up.drop(1)
        val question = arranger.line(run, key, Voice.TONE, beatsEach = NOTE_BEATS, tempoBpm = TEMPO, tonicFloor = tonic)
        val telling = ModeVibes.of(mode).mark ?: mode.degrees[2] // the parents are told by their third
        val mark = arranger.line(
            listOf(DegreeNote(telling), DegreeNote(telling), DegreeNote(Degree.ONE)), key, Voice.TONE,
            beatsEach = 1.0, tempoBpm = TEMPO, tonicFloor = tonic,
        )
        return ModeQuizTask(
            itemKey = prefix + mode.name,
            // the key is recorded with the attempt but never shown: its name is the answer
            prompt = Prompt(title = "?", audio = question, key = key),
            level = level, key = key, mode = mode, choices = pad, question = question, drone = arranger.drone(key), mark = mark,
        )
    }

    override fun evaluate(task: ModeQuizTask, answer: Answer): Evaluation {
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.ModeChoice -> Triple(answer.mode == task.mode, answer.mode.ruName, answer.mode.name)
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        return Evaluation(
            correct = correct,
            expectedText = task.mode.ruName,
            givenText = givenText,
            correction = if (correct) null else task.question.then(task.mark, gapBeats = 1.0),
            taskHadSound = true,
            confirmation = if (correct) task.mark else null,
            expectedCode = task.mode.name,
            givenCode = givenCode,
        )
    }

    companion object {
        const val TEMPO = 120.0
        const val NOTE_BEATS = 0.5
        const val PROMOTION_TARGET_MS = 4_000L

        private val LADDER = listOf(
            listOf(Mode.MAJOR, Mode.MINOR),
            listOf(Mode.DORIAN, Mode.MIXOLYDIAN),
            listOf(Mode.PHRYGIAN, Mode.LYDIAN),
            listOf(Mode.HARMONIC_MINOR),
        )

        /** The modes asked and offered on [level]: every level keeps the ones before it. */
        fun pad(level: Int): List<Mode> = LADDER.take(level.coerceIn(1, LADDER.size)).flatten()
    }
}
