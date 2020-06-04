/*
 *     AsyncMC - A fully async, non blocking, thread safe and open source Minecraft server implementation
 *     Copyright (C) 2020 joserobjr@gamemods.com.br
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published
 *     by the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.github.asyncmc.protocol.raknet

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.core.readAvailable

data class HexDump(val dump: String) {
    constructor(bytes: ByteArray): this(bytes.toHexString())
    constructor(input: Input): this(input.readFully())

    fun toByteArray() = dump.hexToByteArray()
    fun toPacket() = ByteReadPacket(toByteArray())

    private companion object {
        fun Input.readFully(): ByteArray {
            val buffer = ByteArray(1024)
            var total = byteArrayOf()
            var read: Int
            while (true) {
                read = readAvailable(buffer)
                if (read == 0) {
                    return total
                }
                total = total.copyOf(total.size + read)
                buffer.copyInto(total, total.size - read, 0, read)
            }
        }

        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
        fun ByteArray.toHexString(): String {
            val hexChars = CharArray(size * 2)
            for (j in indices) {
                val v = this[j].toInt() and 0xFF
                hexChars[j * 2] = HEX_ARRAY[v ushr 4]
                hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
            }
            return String(hexChars)
        }

        fun String.hexToByteArray(): ByteArray {
            val len = length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(this[i], 16) shl 4)
                        + Character.digit(this[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }
}
