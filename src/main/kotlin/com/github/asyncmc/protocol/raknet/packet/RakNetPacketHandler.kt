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
package com.github.asyncmc.protocol.raknet.packet

import com.github.asyncmc.protocol.raknet.RakNetServerBinding
import com.github.asyncmc.protocol.raknet.RakNetSession
import io.ktor.utils.io.core.*
import java.net.InetSocketAddress

@ExperimentalUnsignedTypes
abstract class RakNetPacketHandler(val packetId: UByte) {
    open suspend fun handleNoSession(binding: RakNetServerBinding, sender: InetSocketAddress, data: ByteReadPacket) {
        // Does nothing by default
    }

    open suspend fun handleSession(binding: RakNetServerBinding, session: RakNetSession, data: ByteReadPacket) {
        // Does nothing by default
    }

    companion object {
        const val ID_CONNECTED_PING: UByte = 0x00u
        const val ID_UNCONNECTED_PING: UByte = 0x01u
        const val ID_UNCONNECTED_PING_OPEN_CONNECTIONS: UByte = 0x02u
        const val ID_CONNECTED_PONG: UByte = 0x03u
        const val ID_DETECT_LOST_CONNECTION: UByte = 0x04u
        const val ID_OPEN_CONNECTION_REQUEST_1: UByte = 0x05u
        const val ID_OPEN_CONNECTION_REPLY_1: UByte = 0x06u
        const val ID_OPEN_CONNECTION_REQUEST_2: UByte = 0x07u
        const val ID_OPEN_CONNECTION_REPLY_2: UByte = 0x08u
        const val ID_CONNECTION_REQUEST: UByte = 0x09u
        const val ID_CONNECTION_REQUEST_ACCEPTED: UByte = 0x10u
        const val ID_CONNECTION_REQUEST_FAILED: UByte = 0x11u
        const val ID_ALREADY_CONNECTED: UByte = 0x12u
        const val ID_NEW_INCOMING_CONNECTION: UByte = 0x13u
        const val ID_NO_FREE_INCOMING_CONNECTIONS: UByte = 0x14u
        const val ID_DISCONNECTION_NOTIFICATION: UByte = 0x15u
        const val ID_CONNECTION_LOST: UByte = 0x16u
        const val ID_CONNECTION_BANNED: UByte = 0x17u
        const val ID_INCOMPATIBLE_PROTOCOL_VERSION: UByte = 0x19u
        const val ID_IP_RECENTLY_CONNECTED: UByte = 0x1au
        const val ID_TIMESTAMP: UByte = 0x1bu
        const val ID_NOT_CONNECTED_PONG: UByte = 0x1cu
        const val ID_ADVERTISE_SYSTEM: UByte = 0x1du
        const val ID_USER_PACKET_ENUM: UByte = 0x80u

        val byPacketId = listOf(
            RakNetPacketUnconnectedPing,
            RakNetPacketOpenConnectionRequest,
            RakNetPacketOpenConnectionRequest2
        ).associateBy { it.packetId }
    }
}
