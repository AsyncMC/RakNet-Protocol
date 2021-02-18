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

import com.github.asyncmc.protocol.raknet.listener.RakNetPingListener
import com.github.michaelbull.logging.InlineLogger
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * @author joserobjr
 * @since 2021-01-05
 */
fun main() = runBlocking {
    val log = InlineLogger()
    log.info { "Starting test server" }
    val server = RakNetServer {
        val allInterfaces = InetAddress.getByName("::")
        val ports = intArrayOf(19132, 19133)
        socketAddresses += ports.map { InetSocketAddress(allInterfaces, it) }
        listeners.ping = object : RakNetPingListener {
            override suspend fun onRakNetPing(
                binding: RakNetServerBinding,
                sender: SocketAddress,
                guidClient: Long,
                sentTick: Long
            ): ByteArray {
                return byteArrayOf()
            }
        }
    }
    
    //launch { 
    //    delay(2000)
    //    server.close("Closed after 2 seconds for testing")
    //}
    
    server.awaitClose()
    log.info { "Test finished" }
}
