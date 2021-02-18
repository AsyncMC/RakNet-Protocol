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
import com.github.asyncmc.protocol.raknet.constants.UNCONNECTED_CONSTANT
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import java.net.InetSocketAddress

@ExperimentalUnsignedTypes
object RakNetPacketUnconnectedPing: RakNetPacketHandler(ID_UNCONNECTED_PING) {
    private val SIZE = UNCONNECTED_CONSTANT.size + 8L

    override suspend fun handleNoSession(binding: RakNetServerBinding, sender: InetSocketAddress, data: ByteReadPacket) {
        if (data.remaining < SIZE) {
            return
        }

        val sentTick = data.readLong()
        val magic = data.readBytes(UNCONNECTED_CONSTANT.size)
        if (!UNCONNECTED_CONSTANT.contentEquals(magic)) {
            return
        }
        
        val guidClient = data.readLong()

        val server = binding.server
        val userData = server.listeners.ping.onRakNetPing(binding, sender, guidClient, sentTick)

        val response = buildPacket(35 + userData.size) {
            writeUByte(ID_NOT_CONNECTED_PONG)
            writeLong(sentTick)
            writeLong(server.guid)
            writeFully(UNCONNECTED_CONSTANT)
            writeUShort(userData.size.toUShort())
            writeFully(userData)
        }

        binding.sendWithoutQueue(Datagram(response, sender))
    }
}
