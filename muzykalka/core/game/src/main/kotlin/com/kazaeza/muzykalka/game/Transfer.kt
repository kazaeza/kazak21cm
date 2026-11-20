package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Degree
import com.kazaeza.muzykalka.music.DegreeFormatter
import com.kazaeza.muzykalka.music.DegreeNote
import com.kazaeza.muzykalka.music.DegreeNotation
import com.kazaeza.muzykalka.music.Harmony
import com.kazaeza.muzykalka.music.Key
import com.kazaeza.muzykalka.music.KeyCatalog
import com.kazaeza.muzykalka.music.MidiNote
import com.kazaeza.muzykalka.music.Mode
import com.kazaeza.muzykalka.music.PitchClass
import com.kazaeza.muzykalka.music.Score
import com.kazaeza.muzykalka.music.Voice

/** The two conditions of the transfer check: the sound the trainer is made of, and a band-like texture without any drone. */
enum class Texture { SYNTH, REAL }

/**
 * O-78 «Проверка переноса» (ADR-074, idea 2 of the research notes): a control round, not a lesson. The same kind of question —
 * the step of a chord at a named place of a four-chord loop — is asked in two conditions that alternate: [Texture.SYNTH]
 * (the tonic hum, a scale to set the key, pad chords: what every mode of the app sounds like) and [Texture.REAL] (a guitar
 * that strikes, a bass, a drum kit; no hum; the key is set by a strummed cadence). Each question is a new key and a loop that
 * no mode of the app trains ([LOOPS]), and it is out of the planning: not weighted by earlier answers, not a review item.
 * The gap between the two accuracies is what the Progress screen shows. High scores in a trainer are not known to mean an
 * ear for real music; this is the measurement, not a cure — and with one user the numbers are noisy.
 *
 * Answers reuse [LoopTask] (one scene of one), so the same screen draws it.
 */
class TransferDriver(
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<LoopTask> {
    override val variant: Variant = Variant.TIR_TRANSFER

    override val accepts: Set<Class<out Answer>> = setOf(Answer.ChordChoice::class.java, Answer.Pass::class.java)

    private var asked = 0
    private var startsWithReal: Boolean? = null
    private val recentKeys = ArrayDeque<PitchClass>()

    override fun nextTask(context: DriverContext): LoopTask {
        val r = context.random
        val firstReal = startsWithReal ?: r.nextBoolean().also { startsWithReal = it }
        val texture = if ((asked % 2 == 0) == firstReal) Texture.REAL else Texture.SYNTH
        asked++
        val tonic = PitchClass.ALL.filterNot { it in recentKeys }.let { it[r.nextInt(it.size)] }
        recentKeys.addLast(tonic)
        while (recentKeys.size > KEY_MEMORY) recentKeys.removeFirst()
        val key = KeyCatalog.practical(tonic, Mode.MAJOR)
        val steps = LOOPS[r.nextInt(LOOPS.size)]
        val loop = steps.map { Harmony.diatonicChord(Mode.MAJOR, it) }
        val place = 1 + r.nextInt(loop.size - 1)
        val roman = ChordParser.formatRoman(loop[place])
        val pad = (0..5).map { Harmony.diatonicChord(Mode.MAJOR, it) }
        return when (texture) {
            Texture.SYNTH -> {
                val question = arranger.chordsOverTonic(loop + loop, key, beatsEach = SYNTH_BEATS, tempoBpm = SYNTH_TEMPO)
                LoopTask(
                    itemKey = "transfer:synth:$roman", prompt = Prompt("?", audio = question, key = key), level = 1, key = key,
                    loop = loop, asked = place, scene = 1, scenes = 1, choices = pad, question = question, drone = arranger.drone(key),
                    heard = arranger.chordsOverTonic(loop, key, beatsEach = SYNTH_BEATS, tempoBpm = SYNTH_TEMPO),
                    intro = orientation(key), introDrone = arranger.drone(key), texture = texture,
                )
            }
            Texture.REAL -> {
                val question = arranger.groove(loop + loop, key, beatsEach = REAL_BEATS, tempoBpm = REAL_TEMPO)
                LoopTask(
                    itemKey = "transfer:real:$roman", prompt = Prompt("?", audio = question, key = key), level = 1, key = key,
                    loop = loop, asked = place, scene = 1, scenes = 1, choices = pad, question = question, drone = null,
                    heard = arranger.groove(loop, key, beatsEach = REAL_BEATS, tempoBpm = REAL_TEMPO),
                    intro = arranger.groove(Harmony.cadence(Mode.MAJOR), key, beatsEach = REAL_BEATS, tempoBpm = REAL_TEMPO),
                    introDrone = null, texture = texture,
                )
            }
        }
    }

    override fun evaluate(task: LoopTask, answer: Answer): Evaluation {
        val expected = task.answer
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.ChordChoice -> Triple(
                answer.chord.root.semitones == expected.root.semitones && answer.chord.quality == expected.quality,
                DegreeFormatter.chord(answer.chord, notation), ChordParser.formatRoman(answer.chord),
            )
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        return Evaluation(
            correct = correct,
            expectedText = DegreeFormatter.chord(expected, notation),
            givenText = givenText,
            correction = if (correct) null else task.question.then(task.heard, gapBeats = 0.8),
            taskHadSound = true,
            confirmation = if (correct) task.heard else null,
            expectedCode = ChordParser.formatRoman(expected),
            givenCode = givenCode,
        )
    }

    private fun orientation(key: Key): Score {
        val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
        return arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.25, tempoBpm = SYNTH_TEMPO, tonicFloor = key.tonicAtOrAbove(MidiNote(55)))
    }

    companion object {
        const val SYNTH_TEMPO = 90.0
        const val SYNTH_BEATS = 1.5
        const val REAL_TEMPO = 100.0
        const val REAL_BEATS = 2.0
        private const val KEY_MEMORY = 4

        /** Loops the app trains nowhere else («Петля в трёх тональностях», «Куда пойдёт?» start from other ones): steps, 0 = I. */
        val LOOPS: List<List<Int>> = listOf(
            listOf(0, 2, 5, 4), listOf(0, 4, 1, 3), listOf(0, 5, 2, 3), listOf(0, 3, 1, 4), listOf(0, 2, 1, 4), listOf(0, 5, 4, 3),
        )
    }
}
