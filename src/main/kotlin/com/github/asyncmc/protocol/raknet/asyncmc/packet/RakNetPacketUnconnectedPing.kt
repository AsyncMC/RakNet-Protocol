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
package com.github.asyncmc.protocol.raknet.asyncmc.packet

import com.github.asyncmc.protocol.raknet.asyncmc.RakNetServer
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import java.net.InetSocketAddress

@ExperimentalUnsignedTypes
object RakNetPacketUnconnectedPing: RakNetPacketHandler(ID_NOT_CONNECTED_PING) {
    private val SIZE = NOT_CONNECTED_MAGIC.size + 8L

    override fun handleNoSession(server: RakNetServer, sender: InetSocketAddress, data: ByteReadPacket) {
        if (data.remaining < SIZE) {
            return
        }

        val sentTick = data.readLong()
        val magic = data.readBytes(NOT_CONNECTED_MAGIC.size)
        if (!NOT_CONNECTED_MAGIC.contentEquals(magic)) {
            return
        }

        val userData = server.listener.onPingFromDisconnected(server, sender, sentTick) ?: return

        val response = buildPacket(35 + userData.size) {
            writeUByte(ID_NOT_CONNECTED_PONG)
            writeLong(sentTick)
            writeLong(server.guid)
            writeFully(NOT_CONNECTED_MAGIC)
            writeUShort(userData.size.toUShort())
            writeFully(userData)
        }

        server.send(Datagram(response, sender))
    }
}
