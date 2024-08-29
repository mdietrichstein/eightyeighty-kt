package com.sorrykaputt.eightyeighty

class Memory {
    private val data = ByteArray(0xFFFF + 1)

    fun load(vararg byteArrays: ByteArray, offset: Int = 0) {
        var currentOffset = offset

        byteArrays.forEach { bytes ->
            bytes.forEach {
                data[currentOffset] = it
                currentOffset += 1
            }
        }
    }

    fun readByte(index: Int): Int {
        return data[index].toInt() and 0xFF
    }

    fun readBytes(index: Int, numBytes: Int): Array<Byte> =
        0.until(numBytes).map { i -> data[(index + i) % data.size] }.toTypedArray()

    fun readWord(index: Int): Int {
        return ((data[(index + 1)].toInt() shl 8) or (data[index].toInt() and 0xFF)) and 0xFFFF
    }

    fun writeWord(index: Int, word: Int) {
        data[(index + 1)] = (word shr 8).toByte()
        data[index] = word.toByte()
    }

    fun writeByte(index: Int, byte: Int) {
        data[index] = byte.toByte()
    }
}
