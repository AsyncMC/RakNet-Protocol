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

package com.github.asyncmc.protocol.raknet.listener

import com.github.asyncmc.protocol.raknet.RakNetServerBinding
import java.net.SocketAddress

/**
 * @author joserobjr
 * @since 2021-01-05
 */
interface RakNetPingListener {
    suspend fun onRakNetPing(binding: RakNetServerBinding, sender: SocketAddress, guidClient: Long, sentTick: Long): ByteArray
}