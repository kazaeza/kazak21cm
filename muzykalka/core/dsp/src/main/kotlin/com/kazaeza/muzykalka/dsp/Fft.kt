package com.kazaeza.muzykalka.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 complex FFT. Tables are built once; transforms allocate nothing,
 * so it is safe to call from the audio thread.
 */
class Fft(val size: Int) {
    init {
        require(size >= 2 && Integer.bitCount(size) == 1) { "FFT size must be a power of two, was $size" }
    }

    private val levels = Integer.numberOfTrailingZeros(size)
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }
    private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - levels) }

    /** Forward transform (kernel e^{-2πikn/N}). Arrays must have length [size]. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, inverse = false)

    /** Inverse transform, scaled by 1/N so that inverse(forward(x)) == x. */
    fun inverse(re: DoubleArray, im: DoubleArray) {
        transform(re, im, inverse = true)
        val scale = 1.0 / size
        for (i in 0 until size) {
            re[i] *= scale
            im[i] *= scale
        }
    }

    private fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        require(re.size == size && im.size == size) { "Arrays must have length $size" }
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val c = cosTable[k]
                    val s = if (inverse) sinTable[k] else -sinTable[k]
                    val l = j + half
                    val tre = re[l] * c - im[l] * s
                    val tim = re[l] * s + im[l] * c
                    re[l] = re[j] - tre
                    im[l] = im[j] - tim
                    re[j] += tre
                    im[j] += tim
                    k += step
                }
                start += len
            }
            len *= 2
        }
    }
}
