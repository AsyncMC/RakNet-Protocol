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
import com.github.asyncmc.protocol.raknet.api.RakNetSessionState
import com.github.asyncmc.protocol.raknet.constants.UDP_HEADER_SIZE
import com.github.asyncmc.protocol.raknet.constants.UNCONNECTED_CONSTANT
import com.github.asyncmc.protocol.raknet.util.setIfAbsent
import com.github.asyncmc.protocol.raknet.util.writeBoolean
import com.github.michaelbull.logging.InlineLogger
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.Inet6Address
import java.net.InetSocketAddress
import kotlin.time.ExperimentalTime

@ExperimentalUnsignedTypes
object RakNetPacketOpenConnectionRequest: RakNetPacketHandler(ID_OPEN_CONNECTION_REQUEST_1) {
    private val log = InlineLogger()
    private val SIZE = UNCONNECTED_CONSTANT.size

    @ExperimentalTime
    override suspend fun handleNoSession(binding: RakNetServerBinding, sender: InetSocketAddress, data: ByteReadPacket) {
        if (data.remaining < SIZE) {
            return
        }

        val magic = data.readBytes(UNCONNECTED_CONSTANT.size)
        if (!UNCONNECTED_CONSTANT.contentEquals(magic)) {
            return
        }
        
        val protocolVersion = data.readUByte()
        val mtu = data.remaining.toInt() + 
                1 + // PacketId size
                UNCONNECTED_CONSTANT.size +
                1 + // Protocol version size
                UDP_HEADER_SIZE +
                if (sender.address is Inet6Address) 40 else 20 // IP address size
        
        data.discard() // Null padding
        
        log.debug { "Received request to open a RakNet connection from $sender. MTU: $mtu, Protocol: $protocolVersion" }

        val server = binding.server
        var session = server.sessions[sender]
        when {
            session != null -> replyAlreadyConnected(binding, sender) 
            server.supportedProtocols.let { it.isNotEmpty() && protocolVersion !in it } -> replyIncompatibleVersion(binding, sender)
            server.maxConnections.let { it >= 0 && server.sessions.size >= it } -> replyServerFull(binding, sender)
            !server.listeners.connectionRequest.isRakNetConnectionAllowed(binding, sender) -> replyBanned(binding, sender)
            else -> {
                val job = SupervisorJob(binding.job)
                val stateFlow = MutableStateFlow(RakNetSessionState.CREATED)
                try {
                    log.debug { "Creating a RakNet session for $sender from ${binding.address}" }
                    session = RakNetSession(binding, sender, mtu, protocolVersion, stateFlow, server.maxInactivity, job)
                    if (server.sessions.setIfAbsent(sender, session) == null) {
                        log.debug { "The RakNet session for $sender was created and activated by ${binding.address} successfully" }
                        stateFlow.value = RakNetSessionState.INITIALIZING
                    } else {
                        log.debug { "The RakNet client $sender got an active session by another binding, closing the session which was created by ${binding.address}." }
                        stateFlow.value = RakNetSessionState.CONNECTION_FAILED
                        job.cancel(CancellationException("Another session was opened concurrently"))
                        return
                    }
                    
                    server.listeners.sessionCreated.onRakNetSessionCreated(session)
                    replyAcceptConnection(binding, session)
                } catch (e: Throwable) {
                    log.error(e) { "An unexpected exception happened while setting up the session for $sender at ${binding.address}" }
                    stateFlow.value = RakNetSessionState.CONNECTION_FAILED
                    job.cancel(CancellationException("An unexpected exception happened while setting up the session", e))
                    throw e
                }
            }
        }
    }
    
    private suspend fun replyAcceptConnection(binding: RakNetServerBinding, session: RakNetSession) {
        binding.sendWithoutQueue(Datagram(address = session.remote, packet = buildPacket(1 + UNCONNECTED_CONSTANT.size + 8 + 1 + 2) {
            writeUByte(ID_OPEN_CONNECTION_REPLY_1)
            writeFully(UNCONNECTED_CONSTANT)
            writeLong(binding.server.guid)
            writeBoolean(false) // Use Encryption
            writeShort(session.mtu.toShort())
        }))
    }

    private suspend fun replyBanned(binding: RakNetServerBinding, sender: InetSocketAddress) {
        binding.sendWithoutQueue(Datagram(address = sender, packet = buildPacket(1 + UNCONNECTED_CONSTANT.size + 8) {
            writeUByte(ID_CONNECTION_BANNED)
            writeFully(UNCONNECTED_CONSTANT)
            writeLong(binding.server.guid)
        }))
    }

    private suspend fun replyServerFull(binding: RakNetServerBinding, sender: InetSocketAddress) {
        binding.sendWithoutQueue(Datagram(address = sender, packet = buildPacket(1 + UNCONNECTED_CONSTANT.size + 8) {
            writeUByte(ID_NO_FREE_INCOMING_CONNECTIONS)
            writeFully(UNCONNECTED_CONSTANT)
            writeLong(binding.server.guid)
        }))
    }

    private suspend fun replyIncompatibleVersion(binding: RakNetServerBinding, sender: InetSocketAddress) {
        binding.sendWithoutQueue(Datagram(address = sender, packet = buildPacket(1 + 1 + UNCONNECTED_CONSTANT.size + 8) {
            writeUByte(ID_INCOMPATIBLE_PROTOCOL_VERSION)
            writeUByte(binding.server.supportedProtocols.first())
            writeFully(UNCONNECTED_CONSTANT)
            writeLong(binding.server.guid)
        }))
    }

    private suspend fun replyAlreadyConnected(binding: RakNetServerBinding, sender: InetSocketAddress) {
        binding.sendWithoutQueue(Datagram(address = sender, packet = buildPacket(1 + UNCONNECTED_CONSTANT.size + 8) { 
            writeUByte(ID_ALREADY_CONNECTED)
            writeFully(UNCONNECTED_CONSTANT)
            writeLong(binding.server.guid)
        }))
    }
}
