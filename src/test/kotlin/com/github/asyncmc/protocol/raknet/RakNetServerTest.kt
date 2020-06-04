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
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.stub
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.ConnectedDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.aSocket
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.readUByte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.channels.AlreadyBoundException
import java.util.*
import kotlin.random.Random
import kotlin.test.assertEquals

@ExperimentalUnsignedTypes
@ExtendWith(MockitoExtension::class)
internal class RakNetServerTest {
    @Mock
    lateinit var listener: RakNetListener
    lateinit var server: RakNetServer

    @BeforeEach
    fun setup() {
        println("Starting...")
        server = RakNetServer(InetSocketAddress(Inet4Address.getLocalHost(), 0), listener)
        server.start()
        println("Started")
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        runBlocking {
            server.join()
        }
    }

    @Test
    fun notConnectedPing() {
        val userData = byteArrayOf(1,2,3)
        listener.stub { on { onPingFromDisconnected(any(), any(), any()) }.thenReturn(userData) }

        client {
            withTimeout(5000) {
                send("010000000004C7A17400FFFF00FEFEFEFEFDFDFDFD1234567889A4567638710E0D")
                val response = incoming.receive().packet
                assertEquals(RakNetPacketHandler.ID_NOT_CONNECTED_PONG, response.readUByte())
            }
        }
    }

    private suspend fun ConnectedDatagramSocket.send(hex: String) {
        send(Datagram(HexDump(hex).toPacket(), remoteAddress))
    }

    @OptIn(KtorExperimentalAPI::class)
    fun client(operation: suspend ConnectedDatagramSocket.() -> Unit) {
        val udp = aSocket(ActorSelectorManager(Dispatchers.IO)).udp()
        for (i in 1..10) {
            try {
                println("Connecting...")
                udp.connect(server.address, InetSocketAddress(Inet4Address.getLocalHost(), 0)) {
                    reusePort = true
                }
            } catch (e: AlreadyBoundException) {
                System.err.println(e.toString())
                continue
            } catch (e: SocketException) {
                System.err.println(e.toString())
                if (e.cause is AlreadyBoundException) {
                    continue
                } else {
                    System.err.println("Cause: ${e.cause}")
                    continue
                }
            }.use {
                runBlocking {
                    it.operation()
                }
                return
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val listener = mock<RakNetListener> {
                on { onPingFromDisconnected(any(), any(), any()) }.thenReturn(
                        StringJoiner(";").apply {
                            add("MCPE")
                            add("Test line Line 1")
                            add("390")
                            add("1.14.60")
                            add("13")
                            add("40")
                            add(Random.nextLong().toString())
                            add("This is Line 2")
                            add("Survival")
                            add("1")
                        }.toString().toByteArray()
                )
            }
            val server = RakNetServer(InetSocketAddress(19132), listener)
            server.start()
            runBlocking {
                server.join()
            }
        }
    }
}
