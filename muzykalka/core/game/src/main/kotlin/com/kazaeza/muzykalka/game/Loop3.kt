package com.kazaeza.muzykalka.game

import com.kazaeza.muzykalka.music.Arranger
import com.kazaeza.muzykalka.music.ChordDegree
import com.kazaeza.muzykalka.music.ChordParser
import com.kazaeza.muzykalka.music.Contrasts
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

/**
 * One scene of «Одна петля в трёх тональностях»: a short loop of [loop] chords, in [key], played twice over the humming
 * tonic; the chord at [asked] is to be named by its step. The same loop and the same place come back in [scenes] keys.
 */
data class LoopTask(
    override val itemKey: String,
    override val prompt: Prompt,
    val level: Int,
    val key: Key,
    val loop: List<ChordDegree>,
    /** Where in the loop the asked chord stands, from 0. */
    val asked: Int,
    /** 1-based place of this scene in the round of [scenes]. */
    val scene: Int,
    val scenes: Int,
    val choices: List<ChordDegree>,
    val question: Score,
    /** The tonic hum; null in the transfer check's «real» texture, where there is none. */
    override val drone: List<MidiNote>?,
    /** The loop once through, at an easy pace — the reward after the answer. */
    val heard: Score,
    override val intro: Score? = null,
    override val introDrone: List<MidiNote>? = null,
    /** O-78: which texture of the transfer check this is; null in «Петля в трёх тональностях». */
    val texture: Texture? = null,
) : DroneTask {
    override val replay: Score get() = question

    val answer: ChordDegree get() = loop[asked]
}

/**
 * O-76 «Одна петля в трёх тональностях» (ADR-073, idea 3 of the research notes): a loop of 3–4 chords is played twice over
 * the tonic hum, and the chord at a given place of it is named by its step — then the SAME loop and the SAME question come
 * back in another key, and once more in a third. The key is told on the card each time; what is to be heard is that the
 * answer does not move while the key does — the degree is what is common to «C–F–G» and «A–D–E». The mix-up the user
 * reports («это тональность меняется или аккорд?») is exactly this, taken out of its tangle and shown three times.
 *
 * Level 1 keeps the four open guitar keys (C G D A); level 2 any key; level 3 adds minor loops and the whole key's chords.
 * The first scene opens with the key's scale (2 s, as «Над аккордами»); the answer of the second and the third scene is
 * the first scene's again, so only a wrong first answer is a surprise. Whether this beats staying in one key is a
 * question the research leaves open (a Hooktheory forum says «one key first»), so the scene is in the item key and the
 * time per scene shows what the keys cost.
 */
class Loop3Driver(
    private val notation: DegreeNotation = DegreeNotation.ARABIC,
    private val arranger: Arranger = Arranger(),
) : ModeDriver<LoopTask> {
    override val variant: Variant = Variant.TIR_LOOP3

    override val accepts: Set<Class<out Answer>> = setOf(Answer.ChordChoice::class.java, Answer.Pass::class.java)

    private class Round(val mode: Mode, val steps: List<Int>, val asked: Int, val keys: List<Key>)

    private var round: Round? = null
    private var scene = 0
    private var lastSteps: List<Int>? = null

    override fun nextTask(context: DriverContext): LoopTask {
        val level = context.level.coerceIn(1, Levels.TIR_LOOP3.size)
        var current = round
        if (current == null || scene >= SCENES) {
            current = newRound(context, level)
            round = current
            scene = 0
        }
        val key = current.keys[scene]
        scene++
        val mode = current.mode
        val loop = current.steps.map { Harmony.diatonicChord(mode, it) }
        val asked = current.asked
        val question = arranger.chordsOverTonic(loop + loop, key, beatsEach = LOOP_BEATS, tempoBpm = TEMPO)
        val heard = arranger.chordsOverTonic(loop, key, beatsEach = HEARD_BEATS, tempoBpm = TEMPO)
        val askedRoman = ChordParser.formatRoman(loop[asked])
        return LoopTask(
            itemKey = "loop3:L$level:s$scene:$askedRoman",
            prompt = Prompt(title = "?", audio = question, key = key),
            level = level, key = key, loop = loop, asked = asked, scene = scene, scenes = SCENES,
            choices = pad(mode, level), question = question, drone = arranger.drone(key), heard = heard,
            // every scene is a new key: it is set by its scale before the loop (2 s)
            intro = orientation(key), introDrone = arranger.drone(key),
        )
    }

    private fun newRound(context: DriverContext, level: Int): Round {
        val r = context.random
        val mode = if (level >= 3 && r.nextDouble() < MINOR_SHARE) Mode.MINOR else Mode.MAJOR
        val options = loops(mode, level)
        val steps = ItemWeights.pick(r, options, avoid = lastSteps) { s ->
            // the loops whose asked chords went worst come back more often: the weight of the first scene of each step
            s.drop(1).distinct().sumOf { ItemWeights.weight("loop3:L$level:s1:${ChordParser.formatRoman(Harmony.diatonicChord(mode, it))}", context.history) }
        }
        lastSteps = steps
        // the place asked: any chord that is not the one the loop opens on (the ear is not asked to find home in the first chord)
        val places = steps.indices.filter { steps[it] != steps[0] }
        val asked = ItemWeights.pick(r, places) {
            ItemWeights.weight("loop3:L$level:s1:${ChordParser.formatRoman(Harmony.diatonicChord(mode, steps[it]))}", context.history)
        }
        val pool = (if (level <= 1) OPEN_TONICS else PitchClass.ALL).shuffled(r)
        // three different keys, and not the same first key as the round before
        val keys = pool.take(SCENES).map { KeyCatalog.practical(it, mode) }
        return Round(mode, steps, asked, keys)
    }

    override fun evaluate(task: LoopTask, answer: Answer): Evaluation {
        val asked = task.answer
        val (correct, givenText, givenCode) = when (answer) {
            is Answer.ChordChoice -> Triple(sameChord(answer.chord, asked), DegreeFormatter.chord(answer.chord, notation), ChordParser.formatRoman(answer.chord))
            Answer.Pass -> Triple(false, "не знаю", "?")
            else -> Triple(false, "—", "—")
        }
        // O-74 for a loop: the loop as it would have been with the chosen chord in that place, and then the right one — the last
        val chosen = (answer as? Answer.ChordChoice)?.chord?.takeIf { !correct }
        val contrast = chosen?.let { c ->
            val wrongLoop = task.loop.mapIndexed { i, ch -> if (i == task.asked) c else ch }
            arranger.chordsOverTonic(wrongLoop, task.key, beatsEach = HEARD_BEATS, tempoBpm = TEMPO)
        }
        val fix = (contrast?.then(task.heard, gapBeats = 0.8)) ?: task.question.then(task.heard, gapBeats = 0.8)
        return Evaluation(
            correct = correct,
            expectedText = DegreeFormatter.chord(asked, notation),
            givenText = givenText,
            correction = if (correct) null else fix,
            taskHadSound = true,
            confirmation = if (correct) task.heard else null,
            expectedCode = ChordParser.formatRoman(asked),
            givenCode = givenCode,
            contrast = chosen?.let { Contrasts.chord(it, task.key.mode, notation) },
        )
    }

    private fun sameChord(a: ChordDegree, b: ChordDegree) = a.root.semitones == b.root.semitones && a.quality == b.quality

    private fun orientation(key: Key): Score {
        val up = key.mode.degrees.map { DegreeNote(it) } + DegreeNote(Degree.ONE, 1)
        return arranger.line(up + DegreeNote(Degree.ONE), key, Voice.TONE, beatsEach = 0.25, tempoBpm = TEMPO, tonicFloor = key.tonicAtOrAbove(MidiNote(55)))
    }

    companion object {
        const val SCENES = 3
        const val TEMPO = 90.0
        const val LOOP_BEATS = 1.5
        const val HEARD_BEATS = 1.0
        const val MINOR_SHARE = 0.5
        const val PROMOTION_TARGET_MS = 5_000L

        /** The four keys a guitarist has open shapes for (research: «сначала открытые тональности»). */
        val OPEN_TONICS: List<PitchClass> = listOf(0, 7, 2, 9).map(PitchClass::of)

        /** The steps (0 = I) of the loops of [mode] on [level]: each is 4 chords; the first is where the loop opens. */
        fun loops(mode: Mode, level: Int): List<List<Int>> = if (mode.hasMinorThird) {
            listOf(listOf(0, 6, 5, 6), listOf(0, 5, 2, 6), listOf(0, 3, 4, 0), listOf(0, 3, 6, 2))
        } else if (level <= 1) {
            listOf(listOf(0, 3, 4, 0), listOf(0, 4, 5, 3), listOf(0, 5, 3, 4), listOf(5, 3, 0, 4))
        } else {
            listOf(listOf(0, 3, 4, 0), listOf(0, 4, 5, 3), listOf(0, 5, 3, 4), listOf(5, 3, 0, 4), listOf(0, 1, 4, 0), listOf(0, 2, 3, 4), listOf(0, 5, 1, 4), listOf(0, 3, 5, 4))
        }

        /** The chords offered: those the loops of the level use (so the choice stays short), and on level 3 all seven of the key. */
        fun pad(mode: Mode, level: Int): List<ChordDegree> {
            val steps = if (level >= 3) (0..6).toList() else loops(mode, level).flatten().distinct().sorted()
            return steps.map { Harmony.diatonicChord(mode, it) }
        }
    }
}
