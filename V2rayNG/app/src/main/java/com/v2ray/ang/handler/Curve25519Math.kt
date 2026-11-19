package com.v2ray.ang.handler

import java.math.BigInteger

/**
 * Self-contained pure-Kotlin RFC 7748 Curve25519 Montgomery Ladder implementation.
 * Zero external dependencies; works identically across all Android versions.
 */
object Curve25519Math {
    private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val P_MINUS_2: BigInteger = P.subtract(BigInteger.valueOf(2))

    fun scalarmultBase(kBytes: ByteArray): ByteArray {
        return scalarmult(kBytes, BigInteger.valueOf(9))
    }

    fun scalarmult(kBytes: ByteArray, uPoint: BigInteger): ByteArray {
        val clamped = kBytes.copyOf(32)
        clamped[0] = (clamped[0].toInt() and 248).toByte()
        clamped[31] = (clamped[31].toInt() and 127).toByte()
        clamped[31] = (clamped[31].toInt() or 64).toByte()

        var k = BigInteger.ZERO
        for (i in 31 downTo 0) {
            k = k.shiftLeft(8).or(BigInteger.valueOf((clamped[i].toInt() and 0xFF).toLong()))
        }

        val x1 = uPoint
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = uPoint
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            val daPlusCb = da.add(cb).mod(P)
            val daMinusCb = da.subtract(cb).mod(P)
            x3 = daPlusCb.multiply(daPlusCb).mod(P)
            z3 = x1.multiply(daMinusCb.multiply(daMinusCb).mod(P)).mod(P)

            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e).mod(P))).mod(P)
        }

        if (swap != 0) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val res = x2.multiply(z2.modPow(P_MINUS_2, P)).mod(P)
        val out = ByteArray(32)
        var temp = res
        for (i in 0 until 32) {
            out[i] = temp.and(BigInteger.valueOf(0xFF)).toByte()
            temp = temp.shiftRight(8)
        }
        return out
    }
}
