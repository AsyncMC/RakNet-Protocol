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

import com.github.asyncmc.protocol.raknet.RakNetDatagram.Companion.FLAG_CONTINUOUS_SEND
import com.github.asyncmc.protocol.raknet.RakNetDatagram.Companion.FLAG_VALID
import com.github.asyncmc.protocol.raknet.api.RakNetPriority
import com.github.asyncmc.protocol.raknet.api.RakNetReliability
import com.github.asyncmc.protocol.raknet.api.RakNetSessionState
import com.github.asyncmc.protocol.raknet.api.send
import com.github.asyncmc.protocol.raknet.constants.MAX_ENCAPSULATED_HEADER_SIZE
import com.github.asyncmc.protocol.raknet.constants.RAKNET_DATAGRAM_HEADER_SIZE
import com.github.asyncmc.protocol.raknet.constants.UDP_HEADER_SIZE
import com.github.asyncmc.protocol.raknet.internal.CachedNanoTimeSource
import com.github.asyncmc.protocol.raknet.internal.SlidingWindow
import com.github.asyncmc.protocol.raknet.packet.RakNetPacketHandler
import com.github.michaelbull.logging.InlineLogger
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.seconds
import com.github.asyncmc.protocol.raknet.api.RakNetSession as RakNetSessionAPI

/**
 * @author joserobjr
 * @since 2021-01-05
 */
@OptIn(ExperimentalTime::class, ExperimentalUnsignedTypes::class)
class RakNetSession internal constructor(
    override val binding: RakNetServerBinding,
    override val remote: InetSocketAddress,
    mtu: Int,
    override val protocolVersion: UByte,
    stateFlow: MutableStateFlow<RakNetSessionState>,
    private val maxInactivity: Duration,
    parentJob: Job
): CoroutineScope, RakNetSessionAPI {
    private val log = InlineLogger()
    private val job = Job(parentJob)
    override val coroutineContext = job + CoroutineName("RakNet Session: $remote")
    
    private val sendChannel = Channel<ByteReadPacket>(Channel.Factory.BUFFERED)
    private val _state = stateFlow
    
    override val state = _state.asStateFlow()

    override var guid by Delegates.notNull<Long>(); private set
    private val timeSource = CachedNanoTimeSource()
    private lateinit var slidingWindow: SlidingWindow

    private var datagramMtu = 0
    
    private val lastSplitIndex = atomic(-1)
    private val lastReliabilityWrite = atomic(-1)
    private val lastOrderWriteIndex = Array(16) {
        atomic(-1)
    }
    
    private var mtu: Int = 0
        set(value) {
            field = value.coerceIn(VALID_MTU_RANGE)
            datagramMtu = (field - UDP_HEADER_SIZE) - if (remote.address is Inet6Address) 40 else 20
        }
    
    init {
        this.mtu = mtu
        job.invokeOnCompletion { 
            sendChannel.close(it)
        }
    }
    
    private var lastActivity = TimeSource.Monotonic.markNow()
    
    private var lastPingSendTime = Instant.EPOCH!!
    private var lastConfirmedPing = Instant.EPOCH!!
    private var lastPingDuration = Duration.INFINITE
    
    init {
        startTimeoutCheckJob()
    }
    
    private fun startTimeoutCheckJob() = launch(CoroutineName("Timeout Check")) {
        do {
            delay(maxInactivity - lastActivity.elapsedNow())
        } while (lastActivity.elapsedNow() < maxInactivity)
        close("The RakNet session has timed out")
    }
    
    override suspend fun send(data: ByteReadPacket) {
        sendChannel.send(data)
    }
    
    override fun sendBlocking(data: ByteReadPacket) {
        sendChannel.sendBlocking(data)
    }

    override suspend fun sendWithoutQueue(data: ByteReadPacket) {
        binding.sendWithoutQueue(remote, data)
    }

    internal fun initialize(mtu: Int, guid: Long) {
        check(_state.compareAndSet(RakNetSessionState.INITIALIZING, RakNetSessionState.INITIALIZED)) {
            "Cannot initialize the RakNet session for $remote, the state is not initializing. State: ${_state.value}"
        }
        this.guid = guid
        this.mtu = mtu
        slidingWindow = SlidingWindow(timeSource, mtu)
        markActive()
        startSendJob()
    }
    
    fun markActive() {
        lastActivity = TimeSource.Monotonic.markNow()
    }
    
    private fun startSendJob() = launch(CoroutineName("Send Job")) { 
        
    }
    
    private fun startPingJob() = launch(CoroutineName("Ping Job")) { 
        while (true) {
            sendPing(Instant.now())
            delay(2.seconds)
        }
    }
    
    private suspend fun sendPing(time: Instant) {
        send(RakNetPriority.IMMEDIATE, RakNetReliability.RELIABLE, 9) {
            writeUByte(RakNetPacketHandler.ID_CONNECTED_PING)
            writeLong(time.toEpochMilli())
            lastPingSendTime = time
        }
    }
    
    override suspend fun send(
        content: ByteReadPacket, priority: RakNetPriority, reliability: RakNetReliability, channel: Int
    ) {
        val packets = encapsulate(content.readByteBuffer(), priority, reliability, channel)
        
        if (priority == RakNetPriority.IMMEDIATE) {
            
        }
    }
    
    private suspend fun sendWithoutQueue(datagram: RakNetDatagram) {
        require(datagram.packets.isNotEmpty()) {
            "Cannot send an empty RakNet datagram"
        }
        Memory.of()
        buildPacket { 
            val test: ByteBuffer
            writeFully(test)
        }
        datagram.packets.first().content?.
    }
    
    private suspend fun sendImmediately(packets: Iterable<EncapsulatedPacket>) {
        val now = timeSource.markNow()
        var bandwidthAvailable = datagramMtu - RAKNET_DATAGRAM_HEADER_SIZE
        createRakNetDatagramFlow(packets.asFlow(), AtomicInteger(Int.MAX_VALUE)) { datagramMtu }
            .filterNotNull()
            .collect { sendWithoutQueue(it) }
    }
    
    private fun createRakNetDatagramFlow(
        encapsulatedPackets: Flow<EncapsulatedPacket>, 
        bandwidth: AtomicInteger,
        mtu: () -> Int
    ) = flow<RakNetDatagram?> {
        val packets = mutableListOf<EncapsulatedPacket>()
        var bandwidthAvailable = bandwidth.value
        var size = RAKNET_DATAGRAM_HEADER_SIZE
        var split = false
        
        suspend fun emitDatagram() {
            if (packets.isEmpty()) {
                emit(null)
            } else {
                emit(RakNetDatagram(packets, timeSource.markNow(),
                    flags = if (split) FLAG_CONTINUOUS_SEND or FLAG_VALID else FLAG_VALID
                ))
            }
            split = false
            bandwidthAvailable = bandwidth.value
            size = RAKNET_DATAGRAM_HEADER_SIZE
        }
        
        encapsulatedPackets.collect { packet ->
            val packetSize = packet.size
            while (bandwidthAvailable < packetSize || size + packetSize > mtu()) {
                emitDatagram()
            }
            
            bandwidthAvailable -= packetSize
            size += packetSize
            packets += packet
            if (packet.split) {
                split = true
            }
        }
    }
    
    private fun encapsulate(
        content: ByteBuffer, priority: RakNetPriority, reliability: RakNetReliability, channel: Int
    ): List<EncapsulatedPacket> {
        val maxLength = datagramMtu - MAX_ENCAPSULATED_HEADER_SIZE - RAKNET_DATAGRAM_HEADER_SIZE
        val adjustedReliability: RakNetReliability
        val initialPos = content.position()
        val splitId: UShort
        val parts = if (content.remaining() <= maxLength) {
            adjustedReliability = reliability
            splitId = 0u
            arrayOf(content)
        } else {
            adjustedReliability = when (reliability) {
                RakNetReliability.UNRELIABLE -> RakNetReliability.RELIABLE
                RakNetReliability.UNRELIABLE_SEQUENCED -> RakNetReliability.RELIABLE_SEQUENCED
                RakNetReliability.UNRELIABLE_WITH_ACK_RECEIPT -> RakNetReliability.RELIABLE_ORDERED_WITH_ACK_RECEIPT
                else -> reliability
            }
            
            val split = ((content.remaining() - 1) / maxLength) + 1
            val limit = content.limit()
            splitId = lastSplitIndex.updateAndGet { old -> 
                if (old >= UShort.MAX_VALUE.toInt()) 0 else old + 1 
            }.toUShort()
            
            Array(split) { index ->
                val pos = initialPos + index * maxLength
                content.slice(pos, maxLength.coerceAtMost(limit - pos))
            }
        }
        
        val orderIndex = if (adjustedReliability.ordered) {
            lastOrderWriteIndex[channel].incrementAndGet().toUInt()
        } else 0u
        
        val split = parts.size > 1
        return parts.mapIndexed { index, buffer -> 
            EncapsulatedPacket(
                content = buffer,
                orderingChannel = channel.toUByte(),
                orderingIndex = orderIndex,
                priority = priority,
                reliability = adjustedReliability,
                reliabilityIndex = if (adjustedReliability.reliable) lastReliabilityWrite.incrementAndGet().toUInt() else 0u,
                split = split,
                partIndex = index,
                partCount = parts.size,
                partId = splitId,
            )
        }
    }
    
    override fun close(message: String?, reason: Throwable?) {
        log.debug(reason) { "The RakNetSession $remote in ${binding.address} is being closed. $message" }
        job.cancel(CancellationException(message ?: "The RakNet session was closed", reason))
    }

    companion object {
        private val VALID_MTU_RANGE = 576..1400
    }
}
