package com.github.asyncmc.protocol.raknet

import com.github.asyncmc.protocol.raknet.api.RakNetListeners
import io.ktor.network.sockets.*
import kotlinx.coroutines.Job
import java.net.InetSocketAddress
import java.util.concurrent.ThreadLocalRandom
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

data class RakNetServerConfigurationContext @ExperimentalTime
@ExperimentalUnsignedTypes constructor(
    val socketAddresses: MutableList<InetSocketAddress> = mutableListOf(),
    var udpConfigurator: SocketOptions.UDPSocketOptions.() -> Unit = {},
    var parentJob: Job? = null,
    var guid: Long = ThreadLocalRandom.current().nextLong(),
    var listeners: RakNetListeners = RakNetListeners(),
    val supportedProtocols: MutableSet<UByte> = mutableSetOf(),
    var maxConnections: Int = -1,
    var maxInactivity: Duration = 5.seconds
)
