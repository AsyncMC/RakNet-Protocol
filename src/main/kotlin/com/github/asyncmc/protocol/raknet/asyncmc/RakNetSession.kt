/*
 *     AsyncMC - A fully async, non blocking, thread safe and open source Minecraft server implementation
 *     Copyright (C) 2020  José Roberto de Araújo Júnior <joserobjr@gamemods.com.br>
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
package com.github.asyncmc.protocol.raknet.asyncmc

import com.github.asyncmc.protocol.raknet.api.RakNetSession
import java.net.SocketAddress

class RakNetSession(
    override val clientSocket: SocketAddress,
    override val protocolServer: RakNetServer
): RakNetSession