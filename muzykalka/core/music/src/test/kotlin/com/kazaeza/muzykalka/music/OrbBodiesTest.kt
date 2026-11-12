package com.kazaeza.muzykalka.music

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * O-38: the three bodies. The thread and the sand are checked against what the lab page (docs/design/lab2/orbs.js)
 * sent to the GPU for the same script (scripts/orb_golden.js); the ink script, which is only what goes into the fluid,
 * is checked for what it promises.
 */
class OrbBodiesTest {
    private class Golden(val dt: Double, val events: Map<Int, String>, val indices: List<Int>, val frames: Map<Int, List<Double>>)

    private fun golden(body: String): Golden {
        val lines = checkNotNull(javaClass.getResourceAsStream("/orb-golden.txt")).bufferedReader().readLines()
        val from = lines.indexOf("body $body")
        val to = lines.drop(from + 1).indexOfFirst { it.startsWith("body ") }.let { if (it < 0) lines.size else from + 1 + it }
        val mine = lines.subList(from + 1, to)
        val events = mine.filter { it.startsWith("event ") }.associate { l -> l.split(" ").let { it[1].toInt() to it[2] } }
        return Golden(
            dt = mine.first { it.startsWith("dt ") }.split(" ")[1].toDouble(),
            events = events,
            indices = mine.first { it.startsWith("index ") }.split(" ").drop(1).map { it.toInt() },
            frames = mine.filter { it.startsWith("frame ") }.associate { l -> l.split(" ").let { it[1].toInt() to it.drop(2).map(String::toDouble) } },
        )
    }

    // ------------------------------------------------------------------------------------------ the numbers they share

    @Test
    fun `tension is nothing at the tonic and grows with the roughness of the ratio`() {
        assertEquals(0f, OrbPhysics.tension(0))
        assertEquals(1f, OrbPhysics.tension(1), 0.02f) // 16:15 is the most restless
        assertTrue(OrbPhysics.tension(7) < OrbPhysics.tension(6), "a fifth is calmer than a tritone")
        assertTrue(OrbPhysics.tension(7) < OrbPhysics.tension(4), "and calmer than a major third")
        for (s in 0..11) assertTrue(OrbPhysics.tension(s) in 0f..1f)
        assertEquals(OrbPhysics.tension(3), OrbPhysics.tension(15), "an octave above is the same degree")
    }

    @Test
    fun `the zeros of the Bessel functions are the textbook ones`() {
        assertEquals(2.404826, besselZero(0, 1), 2e-3)
        assertEquals(5.520078, besselZero(0, 2), 2e-3)
        assertEquals(8.653728, besselZero(0, 3), 2e-3)
        assertEquals(3.831706, besselZero(1, 1), 2e-3)
        assertEquals(7.015587, besselZero(1, 2), 2e-3)
        assertEquals(5.135622, besselZero(2, 1), 2e-3)
        assertEquals(6.380162, besselZero(3, 1), 2e-3)
        assertEquals(8.771484, besselZero(5, 1), 2e-3)
        assertEquals(1.0, besselJ(0, 0.0), 1e-9)
    }

    @Test
    fun `a pluck on the string lands in the lower half of the body`() {
        for (x in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
            val (px, py) = OrbPhysics.pluckPoint(x)
            assertTrue(py < 0f && sqrt(px * px + py * py) < 0.75f, "x=$x -> ($px, $py)")
        }
        assertEquals(0f, OrbPhysics.pluckPoint(0.5f).first, 1e-6f)
        assertTrue(OrbPhysics.pluckPoint(0.1f).first < 0f && OrbPhysics.pluckPoint(0.9f).first > 0f)
    }

    // ------------------------------------------------------------------------------------------ the thread

    @Test
    fun `the thread is the thread of the lab page, through a pluck, two answers and a clear`() {
        val g = golden("thread")
        val body = ThreadBody()
        val last = g.frames.keys.max()
        for (f in 1..last) {
            when (g.events[f]) {
                "pluck" -> body.pluck(1f, atan2(-0.7, 0.3).toFloat())
                "answer7" -> body.answer(7)
                "answer1" -> body.answer(1)
                "clear" -> body.clear()
            }
            body.step(g.dt)
            val want = g.frames[f] ?: continue
            body.trace()
            for ((n, i) in g.indices.withIndex()) {
                assertEquals(want[n * 3], body.xs[i].toDouble(), 2e-4, "frame $f point $i x")
                assertEquals(want[n * 3 + 1], body.ys[i].toDouble(), 2e-4, "frame $f point $i y")
                assertEquals(want[n * 3 + 2], body.depths[i].toDouble(), 2e-4, "frame $f point $i depth")
            }
        }
    }

    @Test
    fun `at rest the thread is one ring, and an answer winds it into a ball`() {
        val body = ThreadBody()
        repeat(30) { body.step(1.0 / 60) }
        body.trace()
        for (i in 0..body.points step 100) {
            assertEquals(OrbPhysics.RADIUS * 2, sqrt(body.xs[i] * body.xs[i] + body.ys[i] * body.ys[i]).toDouble(), 1e-4)
        }
        assertEquals(1, body.p)
        body.answer(7) // a fifth: 3:2
        repeat(120) { body.step(1.0 / 60) }
        body.trace()
        assertEquals(3, body.p)
        assertEquals(2, body.q)
        assertEquals(body.xs[0], body.xs[body.points], 1e-4f, "the string is closed")
        assertEquals(body.ys[0], body.ys[body.points], 1e-4f)
        val radii = (0..body.points step 10).map { sqrt(body.xs[it] * body.xs[it] + body.ys[it] * body.ys[it]) }
        assertTrue(radii.max() - radii.min() > 0.2f, "the winding is not a ring any more")
        assertTrue(body.depths.any { it < 0.3f } && body.depths.any { it > 0.7f }, "there is a near and a far side")
        assertTrue(radii.max() < 0.84f * 1.25f, "and it stays in the ball")
        body.clear()
        repeat(300) { body.step(1.0 / 60) }
        assertEquals(1, body.p)
        assertEquals(1, body.q)
    }

    @Test
    fun `the winding overshoots a little, as a spring let go`() {
        val body = ThreadBody()
        body.answer(4)
        var peak = 0.0
        repeat(120) {
            body.step(1.0 / 60)
            peak = max(peak, body.lock)
        }
        assertTrue(peak > 1.0 && peak < 1.3, "peak $peak")
        assertEquals(1.0, body.lock, 0.02)
    }

    @Test
    fun `a pluck rings the string and dies in five seconds, at most four at once`() {
        val body = ThreadBody()
        body.step(1.0 / 60)
        body.trace()
        val still = body.ys.copyOf()
        repeat(6) { body.pluck(1f, 1f * it) }
        body.step(1.0 / 60)
        body.trace()
        assertTrue(body.ys.indices.any { abs(body.ys[it] - still[it]) > 0.01f }, "the string moves")
        repeat(400) { body.step(1.0 / 60) }
        body.trace()
        val rest = body.ys.copyOf()
        body.step(1.0 / 60)
        body.trace()
        assertTrue(rest.indices.all { abs(body.ys[it] - rest[it]) < 1e-3f }, "and has settled")
        assertTrue(body.energy < 0.01)
    }

    @Test
    fun `the ribbon has a vertex for each side of each point`() {
        val body = ThreadBody(points = 64)
        val out = FloatArray(body.ribbonSize)
        body.answer(7)
        repeat(60) { body.step(1.0 / 60) }
        body.ribbon(out)
        assertEquals(65 * 2 * 7, out.size)
        for (i in 0..64) {
            val l = i * 14
            assertEquals(out[l], out[l + 7], "both sides at one point")
            assertEquals(-1f, out[l + 6])
            assertEquals(1f, out[l + 13])
            assertEquals(1.0, (out[l + 2] * out[l + 2] + out[l + 3] * out[l + 3]).toDouble(), 1e-3, "a unit normal")
        }
        assertEquals(0f, out[5])
        assertEquals(2 * Math.PI, out[64 * 14 + 5].toDouble(), 1e-4)
    }

    // ------------------------------------------------------------------------------------------ the sand

    @Test
    fun `the sand is the sand of the lab page, through a pluck, an answer and a clear`() {
        val g = golden("sand")
        val body = SandBody(3000)
        val last = g.frames.keys.max()
        for (f in 1..last) {
            when (g.events[f]) {
                "pluck" -> body.pluck(1f)
                "answer7" -> body.answer(7)
                "clear" -> body.clear()
            }
            body.step(g.dt)
            val want = g.frames[f] ?: continue
            for ((n, i) in g.indices.withIndex()) {
                assertEquals(want[n * 3], body.out[i * 3].toDouble(), 2e-3, "frame $f grain $i x")
                assertEquals(want[n * 3 + 1], body.out[i * 3 + 1].toDouble(), 2e-3, "frame $f grain $i y")
                assertEquals(want[n * 3 + 2], body.out[i * 3 + 2].toDouble(), 2e-3, "frame $f grain $i heat")
            }
        }
    }

    @Test
    fun `the sand settles on the nodal lines of the figure and stays on the plate`() {
        val body = SandBody(6000)
        body.answer(0) // the tonic: rings of the second zero of J0 (plus the rim)
        repeat(400) { body.step(1.0 / 60) }
        val scale = OrbPhysics.RADIUS * 2
        var onNode = 0
        for (i in 0 until body.count) {
            val r = sqrt(body.out[i * 3] * body.out[i * 3] + body.out[i * 3 + 1] * body.out[i * 3 + 1]) / scale
            assertTrue(r <= 0.9851, "grain $i at $r is off the plate")
            if (abs(r - besselZero(0, 1) / besselZero(0, 2)) < 0.06) onNode++
        }
        // the band holds ~10 % of the plate: sand that has not settled would put ~10 % there, a figure puts far more
        assertTrue(onNode > 0.25 * body.count, "only ${100.0 * onNode / body.count} % on the nodal circle")
    }

    @Test
    fun `an answer picks the figure, p diameters and q circles, and a clear takes the rings back`() {
        val body = SandBody(100)
        assertEquals(0, body.figureDiameters)
        body.answer(7)
        assertEquals(3, body.figureDiameters)
        body.answer(3) // 6:5
        assertEquals(6, body.figureDiameters)
        body.clear()
        assertEquals(0, body.figureDiameters)
        assertEquals(-1, body.semitone)
    }

    @Test
    fun `the sand runs in real time`() {
        val body = SandBody()
        body.answer(7)
        repeat(20) { body.step(1.0 / 60) } // warm the JIT
        val t0 = System.nanoTime()
        repeat(60) { body.step(1.0 / 60) }
        val ms = (System.nanoTime() - t0) / 1e6 / 60
        // on a desktop JVM; a phone is slower, but this is the cost of the 24 000 grains and it must not be silly
        assertTrue(ms < 8.0, "$ms ms per frame")
    }

    // ------------------------------------------------------------------------------------------ the ink

    private fun frame(script: InkScript, dt: Double = 1.0 / 60): InkScript.InkFrame = InkScript.InkFrame().also { script.step(dt, it) }

    private fun InkScript.InkFrame.count(which: Int, ofRole: Int? = null) =
        (0 until count).count { kind[it] == which && (ofRole == null || role[it] == ofRole) }

    @Test
    fun `at rest four sources keep the ink moving`() {
        val f = frame(InkScript())
        assertEquals(4, f.count(InkScript.InkFrame.VELOCITY))
        assertEquals(4, f.count(InkScript.InkFrame.DYE))
        assertEquals(0, f.count(InkScript.InkFrame.BURST))
        for (i in 0 until f.count) assertTrue(f.x[i] in 0.1f..0.9f && f.y[i] in 0.1f..0.9f, "inside the ball")
        assertEquals(0.16f, f.dyeDissipation, 1e-6f)
    }

    @Test
    fun `a pluck is a burst and a puff of ink, once`() {
        val s = InkScript()
        s.pluck(0.8f, 0f, -0.7f)
        val f = frame(s)
        assertEquals(1, f.count(InkScript.InkFrame.BURST))
        assertEquals(1, f.count(InkScript.InkFrame.DYE, InkScript.ROLE_BURST))
        val b = (0 until f.count).first { f.kind[it] == InkScript.InkFrame.BURST }
        assertEquals(0.5f, f.x[b], 1e-6f)
        assertEquals(0.5f - 0.42f * 0.7f, f.y[b], 1e-6f)
        assertEquals(430f * 0.8f, f.a[b], 1e-3f)
        assertEquals(0, frame(s).count(InkScript.InkFrame.BURST), "it is not repeated")
    }

    @Test
    fun `an answer in the ratio p to q is p jets, and the tonic only tints`() {
        val s = InkScript()
        s.answer(7) // 3:2
        val f = frame(s)
        assertEquals(3, f.count(InkScript.InkFrame.DYE, InkScript.ROLE_JET))
        assertEquals(4 + 3, f.count(InkScript.InkFrame.VELOCITY))
        assertTrue(f.dyeDissipation > 1.0f, "the old ink is washed out at once")
        val calm = (0 until f.count).first { f.kind[it] == InkScript.InkFrame.VELOCITY && f.role[it] == 0 }
        // the four sources push a quarter as hard while a degree stands
        val rest = frame(InkScript())
        assertTrue(sqrt(f.a[calm] * f.a[calm] + f.b[calm] * f.b[calm]) < 0.3f * sqrt(rest.a[0] * rest.a[0] + rest.b[0] * rest.b[0]) + 1e-6f)
        repeat(300) { frame(s) }
        assertEquals(0.16f, frame(s).dyeDissipation, 0.01f, "and the wash dies away")
        s.clear()
        assertEquals(0, frame(s).count(InkScript.InkFrame.DYE, InkScript.ROLE_JET))
    }

    @Test
    fun `a rough ratio flickers and creeps, a simple one stands still`() {
        fun jetsAt(semi: Int, steps: Int): Float {
            val s = InkScript()
            s.answer(semi)
            var fr = frame(s)
            repeat(steps) { fr = frame(s) }
            val j = (0 until fr.count).first { fr.kind[it] == InkScript.InkFrame.DYE && fr.role[it] == InkScript.ROLE_JET }
            return fr.x[j]
        }
        // the tonic (a rest) stands still at the same place; a minor second has turned by now
        assertEquals(jetsAt(7, 5), jetsAt(7, 5), 0f)
        assertTrue(abs(jetsAt(1, 200) - jetsAt(1, 5)) > 0.01f)
    }
}
