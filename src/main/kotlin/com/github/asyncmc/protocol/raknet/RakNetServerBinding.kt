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

package com.github.asyncmc.protocol.raknet

import com.github.asyncmc.protocol.raknet.packet.RakNetPacketHandler
import com.github.michaelbull.logging.InlineLogger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.SelectClause0
import java.net.InetSocketAddress
import com.github.asyncmc.protocol.raknet.api.RakNetServerBinding as RakNetServerBindingAPI

/**
 * @author joserobjr
 * @since 2021-01-05
 */
class RakNetServerBinding internal constructor(
    override val server: RakNetServer,
    val address: InetSocketAddress,
    udpConfigurator: SocketOptions.UDPSocketOptions.() -> Unit = {},
    parentJob: Job
): CoroutineScope, Closeable, RakNetServerBindingAPI {
    private val log = InlineLogger()
    val job = Job(parentJob)
    override val coroutineContext = job + CoroutineName("RakNet Binding: $address") + Dispatchers.IO
    
    private var _binding: BoundDatagramSocket? = null
    private val binding: BoundDatagramSocket get() = _binding ?: runBlocking(coroutineContext) { bindingAsync.await() }
    
    val onClose: SelectClause0 get() = job.onJoin
    override val guid: Long
        get() = server.guid
    
    @OptIn(KtorExperimentalAPI::class)
    private val bindingAsync = async {
        log.debug { "Binding $address to the RakNet server ${server.name}" }
        aSocket(ActorSelectorManager(coroutineContext)).udp().bind(address, udpConfigurator).also { 
            _binding = it
            log.debug { "The $address has been bound to the RakNet server ${server.name} successfully" }
            listen()
        }
    }
    
    private fun listen() = launch { 
        while (job.isActive) {
            val datagram = binding.receive()
            val socketAddress = datagram.address
            val packet = datagram.packet
            if (packet.isEmpty || socketAddress !is InetSocketAddress || socketAddress.address in server.blockedAddresses) {
                datagram.packet.release()
                continue
            }
            
            launch {
                try {
                    acceptDatagram(datagram, socketAddress)
                } finally {
                    datagram.packet.release()
                }
            }
        }
    }
    
    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun acceptDatagram(datagram: Datagram, address: InetSocketAddress) {
        val packetId = datagram.packet.readUByte()
        val handler = RakNetPacketHandler.byPacketId[packetId]
        
        val session = server.sessions[datagram.address]
        when {
            handler == null -> server.listeners.unknownPacket.onUnknownRakNetPacket(this, datagram.packet, session)
            session == null -> handler.handleNoSession(this, address, datagram.packet)
            else -> handler.handleSession(this, session, datagram.packet)
        }
        if (!datagram.packet.endOfInput) {
            log.warn { "The RakNet packet 0x${packetId.toString(16).padStart(2, '0')} from ${session ?: datagram.address} was not read fully (${datagram.packet.remaining} bytes remaining)" }
        }
    }

    override suspend fun sendWithoutQueue(target: InetSocketAddress, buffer: ByteReadPacket) {
        sendWithoutQueue(Datagram(buffer, target))
    }

    suspend fun sendWithoutQueue(datagram: Datagram) {
        (_binding ?: bindingAsync.await())
            .send(datagram)
    }

    override fun close() {
        job.cancel()
    }

    fun close(message: String? = null, cause: Throwable? = null) {
        job.cancel(CancellationException(
            cause = cause,
            message = message.takeUnless { it.isNullOrEmpty() }
        ))
    }
}
