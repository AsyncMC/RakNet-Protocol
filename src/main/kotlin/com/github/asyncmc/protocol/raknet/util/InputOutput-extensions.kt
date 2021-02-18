/*
 *     AsyncMC - A fully async, non blocking, thread safe and open source Minecraft server implementation
 *     Copyright (C) 2021 joserobjr
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

package com.github.asyncmc.protocol.raknet.util

import io.ktor.utils.io.core.*
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress

/**
 * @author joserobjr
 * @since 2021-01-06
 */
private const val TRUE: Byte = 1
private const val FALSE: Byte = 0

internal fun Output.writeBoolean(value: Boolean) {
    writeByte(if (value) TRUE else FALSE)
}

internal fun Input.readBoolean() = readByte() == TRUE

@OptIn(ExperimentalUnsignedTypes::class)
private const val IPV4: UByte = 4u
@OptIn(ExperimentalUnsignedTypes::class)
private const val IPV6: UByte = 6u

@OptIn(ExperimentalUnsignedTypes::class)
internal fun Input.readSocketAddress(): InetSocketAddress {
    return when (val version = readUByte()) {
        IPV4 -> readIPv4Socket()
        IPV6 -> readIPv6Socket()
        else -> throw UnsupportedOperationException("Unsupported IP version $version")
    }
}

internal fun Output.writeSocketAddress(socketAddress: InetSocketAddress) {
    when (val ip = socketAddress.address) {
        is Inet4Address -> writeIPv4Socket(ip, socketAddress.port)
        is Inet6Address -> writeIPv6Socket(ip, socketAddress.port)
        else -> throw UnsupportedOperationException("Unsupported IP address instance: $ip")
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun Input.readIPv4Socket(): InetSocketAddress {
    val ip = Inet4Address.getByAddress(readBytes(4))
    val port = readUShort().toInt()
    return InetSocketAddress(ip, port)
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun Output.writeIPv4Socket(ip: Inet4Address, port: Int) {
    writeUByte(IPV4)
    writeFully(ip.address)
    writeUShort(port.toUShort())
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun Input.readIPv6Socket(): InetSocketAddress {
    val family = readShortLittleEndian()
    val port = readUShort().toInt()
    val flow = readInt()
    val address = readBytes(16)
    val scopeId = readInt()
    
    val ip = Inet6Address.getByAddress(null, address, scopeId)
    return InetSocketAddress(ip, port)
}

private const val AF_INET6: Short = 23
@OptIn(ExperimentalUnsignedTypes::class)
private fun Output.writeIPv6Socket(ip: Inet6Address, port: Int) {
    writeUByte(IPV6)
    writeShortLittleEndian(AF_INET6)
    writeUShort(port.toUShort())
    writeInt(0) // flow
    writeFully(ip.address)
    writeInt(ip.scopeId)
}

@ExperimentalUnsignedTypes
fun Output.writeUMediumLittleEndian(value: UInt) {
    writeMediumLittleEndian(value.toInt())
}

fun Output.writeMediumLittleEndian(value: Int) {
    writeByte(value.toByte())
    writeByte((value ushr 8).toByte())
    writeByte((value ushr 16).toByte())
}

@OptIn(ExperimentalUnsignedTypes::class)
fun Input.readMediumLittleEndian(): Int {
    val unsigned = readUMediumLittleEndian()
    return if ((unsigned and 0x800000u) != 0u) {
        unsigned and 0xff000000u
    } else {
        unsigned
    }.toInt()
}

@ExperimentalUnsignedTypes
fun Input.readUMediumLittleEndian(): UInt {
    return readUByte().toUInt() and (readUByte().toUInt() shl 8) and (readUByte().toUInt() shl 16)
}
