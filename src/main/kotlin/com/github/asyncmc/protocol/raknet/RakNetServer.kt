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

import com.github.asyncmc.protocol.raknet.packet.RakNetPacketHandler
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.aSocket
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readUByte
import kotlinx.coroutines.*
import org.jctools.maps.NonBlockingHashMap
import org.jctools.maps.NonBlockingHashSet
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

class RakNetServer(
        private val socketAddress: InetSocketAddress,
        internal val listener: RakNetListener
) {
    val guid = ThreadLocalRandom.current().nextLong()
    private val started = AtomicBoolean(false)
    private val niceShutdown = AtomicBoolean(false)
    private lateinit var binding: BoundDatagramSocket
    private lateinit var rakNetScope: CoroutineScope

    val job get() = rakNetScope.coroutineContext[Job]!!
    val blockedAddresses: MutableSet<InetAddress> = NonBlockingHashSet()
    val sessions: MutableMap<InetSocketAddress, RakNetSession> = NonBlockingHashMap()

    val address get() = binding.localAddress

    internal fun send(datagram: Datagram): Job {
        checkIsRunning()
        return rakNetScope.launch {
            binding.outgoing.send(datagram)
        }
    }

    private fun checkIsRunning() {
        if (!started.get()) {
            throw IOException("The server is not running")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun handle(datagram: Datagram) {
        val packetId = datagram.packet.readUByte()
        val session = sessions[datagram.address]
        val handler = RakNetPacketHandler.byPacketId[packetId]
        if (handler == null) {
            listener.onUnknownDatagram(this, session, datagram)
            return
        }
        if (session != null) {
            handler.handleSession(this, session, datagram.packet)
        } else {
            handler.handleNoSession(this, datagram.address, datagram.packet)
        }
    }

    private suspend fun listen() {
        while (true) {
            try {
                val datagram = binding.incoming.receive()
                val address = datagram.address
                val packet = datagram.packet
                if (packet.isEmpty || address is InetSocketAddress && address.address in blockedAddresses) {
                    packet.release()
                    continue
                }
                coroutineScope {
                    launch(Dispatchers.Default) {
                        try {
                            handle(datagram)
                        } finally {
                            packet.release()
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    if (!niceShutdown.get()) {
                        e.printStackTrace()
                    }
                }
                try {
                    stop()
                } catch (e2: Throwable) {
                    e.addSuppressed(e2)
                }
                throw e
            } finally {
                try {
                    stop()
                } finally {
                    started.compareAndSet(true, false)
                }
            }
        }
    }

    @OptIn(KtorExperimentalAPI::class)
    fun start() {
        check(started.compareAndSet(false, true))
        niceShutdown.set(false)
        try {
            binding = aSocket(ActorSelectorManager(Dispatchers.IO)).udp().bind(socketAddress)
        } catch (e: Throwable) {
            started.compareAndSet(true, false)
            throw e
        }

        rakNetScope = CoroutineScope(Dispatchers.IO)
        rakNetScope.launch {
            listen()
        }
    }

    fun stop(message: String? = null, cause: Throwable? = null) {
        if (!started.get()) return
        try {
            listOf(
                    tryCatching { rakNetScope.takeIf { it.isActive }?.cancel(CancellationException(message, cause)) },
                    tryCatching { binding.close() }
            ).throwIfAny()
            if (started.get()) {
                niceShutdown.set(true)
            }
        } finally {
            started.compareAndSet(true, false)
        }
    }

    private fun List<Exception?>.throwIfAny() {
        combined()?.let { throw it }
    }

    private fun List<Exception?>.combined(): Exception? {
        val first = indexOfFirst { it != null }
        if (first < 0) return null
        val base = this[first]!!
        subList(first + 1, size)
                .asSequence()
                .filterNotNull()
                .forEach { base.addSuppressed(it) }
        return base
    }

    private inline fun tryCatching(operation: () -> Unit): Exception? {
        return try {
            operation()
            null
        } catch (exception: Exception) {
            exception
        }
    }

    suspend fun join() = job.join()
}
