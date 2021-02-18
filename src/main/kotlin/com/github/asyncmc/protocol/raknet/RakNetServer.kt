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

import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.SelectClause0
import kotlinx.coroutines.selects.select
import org.jctools.maps.NonBlockingHashMap
import org.jctools.maps.NonBlockingHashSet
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.ExperimentalTime
import com.github.asyncmc.protocol.raknet.api.RakNetServer as RakNetServerAPI

/**
 * @author joserobjr
 * @since 2021-01-05
 */
class RakNetServer private constructor(configs: RakNetServerConfigurationContext): RakNetServerAPI {
    private val _socketAddresses = configs.socketAddresses.distinct().toTypedArray()
    init {
        require(_socketAddresses.isNotEmpty()) {
            "At least one socket address is required to open a RakNet server."
        }
        
        require(_socketAddresses.none { it.isUnresolved }) {
            "The socket addresses cannot be unresolved. Unresolved: ${_socketAddresses.filter { it.isUnresolved }}"
        }
    }
    
    private val log = InlineLogger()
    private val udpConfigurator = configs.udpConfigurator
    val guid = configs.guid
    var maxConnections = configs.maxConnections
    val listeners = configs.listeners.copy()
    val supportedProtocols = configs.supportedProtocols.toSet()
    val maxInactivity = configs.maxInactivity
    val blockedAddresses: MutableSet<InetAddress> = NonBlockingHashSet()
    val sessions: MutableMap<InetSocketAddress, RakNetSession> = NonBlockingHashMap()
    
    private val socketAddresses get() = _socketAddresses.toList()
    val name = socketAddresses.toString()
    private val job = Job(configs.parentJob)
    private val coroutineScope = CoroutineScope(job + CoroutineName("RakNet server $name") + Dispatchers.IO)

    private val bindingsSupervisor = SupervisorJob(job)
    override val bindings = try {
        _socketAddresses.mapTo(mutableListOf(), this::bind)
    } catch (e: Exception) {
        job.cancel(CancellationException("Exception while binding the UDP ports", e))
        throw e
    }
    
    val onClose: SelectClause0 get() = job.onJoin
    
    init {
        with(coroutineScope) {
            launch {
                while (job.isActive) {
                    select<Unit> {
                        bindings.forEachIndexed { index, binding ->
                            binding.onClose {
                                log.warn { "The RakNet binding ${binding.address} was closed unexpectedly, reopening..." }
                                bindings[index] = bind(binding.address)
                                delay(1000)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun bind(address: InetSocketAddress): RakNetServerBinding {
        return RakNetServerBinding(this,
            address, udpConfigurator,
            bindingsSupervisor
        )
    }
    
    suspend fun awaitClose() {
        job.join()
    }
    
    override fun close() {
        close(null)
    }

    fun close(message: String? = null, cause: Throwable? = null) {
        log.debug(cause) { "The RakNet server $name is being closed. $message" }
        job.cancel(CancellationException(
            cause = cause,
            message = message.takeUnless { it.isNullOrEmpty() } ?: "The RakNet server $name is being closed"
        ))
    }
    
    companion object {
        @OptIn(ExperimentalTime::class, ExperimentalUnsignedTypes::class)
        @JvmName("createInstance")
        operator fun invoke(configurator: RakNetServerConfigurationContext.() -> Unit): RakNetServer {
            val config = RakNetServerConfigurationContext().apply(configurator)
            return RakNetServer(config)
        }
    }
}
