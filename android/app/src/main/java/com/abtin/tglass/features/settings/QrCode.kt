package com.abtin.tglass.features.settings

/**
 * Minimal QR Code encoder (byte mode, error correction level M, versions 1–10, i.e. up to 213 bytes).
 * Follows the structure of Project Nayuki's QR Code generator (MIT License); mask selection is fixed
 * because every mask pattern is valid for decoders.
 */
object QrCode {
    // Index = version (0 unused). Level M only.
    private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
    private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)
    private const val FORMAT_BITS_M = 0
    private const val MASK = 2

    /** Returns the module matrix ([y][x], true = dark), or null if [text] is too long. */
    fun encode(text: String): Array<BooleanArray>? {
        val data = text.toByteArray(Charsets.UTF_8)
        var version = 0
        for (v in 1..10) {
            val countBits = if (v <= 9) 8 else 16
            if (4 + countBits + data.size * 8 <= dataCodewords(v) * 8) {
                version = v
                break
            }
        }
        if (version == 0) return null

        // Data bits: mode (byte = 0100), length, payload, terminator, padding.
        val bits = ArrayList<Boolean>()
        fun append(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
        }
        append(4, 4)
        append(data.size, if (version <= 9) 8 else 16)
        for (b in data) append(b.toInt() and 0xFF, 8)
        val capacity = dataCodewords(version) * 8
        append(0, minOf(4, capacity - bits.size))
        append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacity) {
            append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        val codewords = ByteArray(bits.size / 8)
        for (i in bits.indices) if (bits[i]) codewords[i ushr 3] = (codewords[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()

        val all = addEccAndInterleave(version, codewords)
        val qr = Matrix(version * 4 + 17)
        qr.drawFunctionPatterns(version)
        qr.drawCodewords(all)
        qr.applyMask(MASK)
        qr.drawFormatBits(MASK)
        return qr.modules
    }

    private fun rawDataModules(ver: Int): Int {
        var result = (16 * ver + 128) * ver + 64
        if (ver >= 2) {
            val numAlign = ver / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (ver >= 7) result -= 36
        }
        return result
    }

    private fun dataCodewords(ver: Int): Int = rawDataModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver]

    private fun addEccAndInterleave(ver: Int, data: ByteArray): ByteArray {
        val numBlocks = NUM_BLOCKS[ver]
        val blockEccLen = ECC_PER_BLOCK[ver]
        val rawCodewords = rawDataModules(ver) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks
        val divisor = rsDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val block = dat.copyOf(shortBlockLen + 1)
            val ecc = rsRemainder(dat, divisor)
            System.arraycopy(ecc, 0, block, block.size - blockEccLen, ecc.size)
            blocks.add(block)
        }
        val result = ByteArray(rawCodewords)
        var n = 0
        for (i in blocks[0].indices) {
            for (j in blocks.indices) {
                // Skip the padding byte of short blocks.
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[n++] = blocks[j][i]
                }
            }
        }
        return result
    }

    private fun rsMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun rsDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in result.indices) {
                result[j] = rsMultiply(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = rsMultiply(root, 0x02)
        }
        return result
    }

    private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
        val result = IntArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor result[0]
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in result.indices) result[i] = result[i] xor rsMultiply(divisor[i], factor)
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    private class Matrix(val size: Int) {
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            isFunction[y][x] = true
        }

        fun drawFunctionPatterns(version: Int) {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val pos = alignmentPositions(version)
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                if (!(i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0)) alignment(pos[i], pos[j])
            }
            drawFormatBits(0) // reserve; redrawn after masking
            drawVersion(version)
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) set(xx, yy, dist != 2 && dist != 4)
            }
        }

        private fun alignment(x: Int, y: Int) {
            for (dy in -2..2) for (dx in -2..2) set(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }

        private fun alignmentPositions(version: Int): IntArray {
            if (version == 1) return IntArray(0)
            val numAlign = version / 7 + 2
            val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var p = size - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = p
                p -= step
            }
            return result
        }

        private fun bit(x: Int, i: Int) = ((x ushr i) and 1) != 0

        fun drawFormatBits(mask: Int) {
            val data = (FORMAT_BITS_M shl 3) or mask
            var rem = data
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = ((data shl 10) or rem) xor 0x5412
            for (i in 0..5) set(8, i, bit(bits, i))
            set(8, 7, bit(bits, 6))
            set(8, 8, bit(bits, 7))
            set(7, 8, bit(bits, 8))
            for (i in 9 until 15) set(14 - i, 8, bit(bits, i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(bits, i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(bits, i))
            set(8, size - 8, true)
        }

        private fun drawVersion(version: Int) {
            if (version < 7) return
            var rem = version
            for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
            val bits = (version shl 12) or rem
            for (i in 0 until 18) {
                val b = bit(bits, i)
                val a = size - 11 + i % 3
                val c = i / 3
                set(a, c, b)
                set(c, a, b)
            }
        }

        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0 until 2) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!isFunction[y][x] && i < data.size * 8) {
                            modules[y][x] = bit(data[i ushr 3].toInt() and 0xFF, 7 - (i and 7))
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
            }
        }
    }
}
