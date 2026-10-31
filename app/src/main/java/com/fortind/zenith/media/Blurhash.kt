package com.fortind.zenith.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

object Blurhash {
    private const val CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    fun decode(hash: String?, width: Int, height: Int, punch: Float = 1f): IntArray? {
        if (hash == null || hash.length < 6 || width <= 0 || height <= 0) return null
        val sizeFlag = decode83(hash, 0, 1) ?: return null
        val numY = sizeFlag / 9 + 1
        val numX = sizeFlag % 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null
        val quantisedMax = decode83(hash, 1, 2) ?: return null
        val maxValue = (quantisedMax + 1) / 166f
        val colors = Array(numX * numY) { index ->
            if (index == 0) {
                decodeDc(decode83(hash, 2, 6) ?: return null)
            } else {
                decodeAc(decode83(hash, 4 + index * 2, 6 + index * 2) ?: return null, maxValue * punch)
            }
        }
        val pixels = IntArray(width * height)
        val cosX = Array(numX) { i -> FloatArray(width) { x -> cos(PI * x * i / width).toFloat() } }
        val cosY = Array(numY) { j -> FloatArray(height) { y -> cos(PI * y * j / height).toFloat() } }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0f
                var g = 0f
                var b = 0f
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = cosX[i][x] * cosY[j][y]
                        val color = colors[i + j * numX]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }
                pixels[x + y * width] = (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return pixels
    }

    private fun decode83(text: String, from: Int, to: Int): Int? {
        var value = 0
        for (index in from until to) {
            val digit = CHARACTERS.indexOf(text[index])
            if (digit < 0) return null
            value = value * 83 + digit
        }
        return value
    }

    private fun decodeDc(value: Int): FloatArray = floatArrayOf(
        srgbToLinear(value shr 16),
        srgbToLinear((value shr 8) and 0xFF),
        srgbToLinear(value and 0xFF),
    )

    private fun decodeAc(value: Int, maxValue: Float): FloatArray = floatArrayOf(
        signedPow((value / (19 * 19) - 9) / 9f) * maxValue,
        signedPow((value / 19 % 19 - 9) / 9f) * maxValue,
        signedPow((value % 19 - 9) / 9f) * maxValue,
    )

    private fun signedPow(value: Float): Float = sign(value) * abs(value).pow(2f)

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val srgb = if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1 / 2.4f) - 0.055f
        return (srgb * 255 + 0.5f).toInt().coerceIn(0, 255)
    }

    fun averageColor(hash: String?): Int? {
        if (hash == null || hash.length < 6) return null
        val value = decode83(hash, 2, 6) ?: return null
        return (0xFF shl 24) or value
    }
}
