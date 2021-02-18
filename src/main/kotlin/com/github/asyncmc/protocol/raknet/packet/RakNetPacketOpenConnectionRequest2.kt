package com.github.asyncmc.protocol.raknet.packet

import com.github.asyncmc.protocol.raknet.RakNetServerBinding
import com.github.asyncmc.protocol.raknet.RakNetSession
import com.github.asyncmc.protocol.raknet.api.RakNetSessionState
import com.github.asyncmc.protocol.raknet.constants.UNCONNECTED_CONSTANT
import com.github.asyncmc.protocol.raknet.util.readSocketAddress
import com.github.asyncmc.protocol.raknet.util.writeBoolean
import com.github.asyncmc.protocol.raknet.util.writeSocketAddress
import io.ktor.utils.io.core.*

/**
 * @author joserobjr
 * @since 2021-01-06
 */
@ExperimentalUnsignedTypes
object RakNetPacketOpenConnectionRequest2: RakNetPacketHandler(ID_OPEN_CONNECTION_REQUEST_1) {
    override suspend fun handleSession(binding: RakNetServerBinding, session: RakNetSession, data: ByteReadPacket) {
        if (session.state.value != RakNetSessionState.INITIALIZING) {
            return
        }

        val magic = data.readBytes(UNCONNECTED_CONSTANT.size)
        if (!UNCONNECTED_CONSTANT.contentEquals(magic)) {
            return
        }
        
        val address = data.readSocketAddress()
        if (address != session.remote) {
            return
        }
        
        val mtu = data.readUShort().toInt()
        val guid = data.readLong()
        session.initialize(mtu, guid)
        sendReply(session)
    }
    
    private suspend fun sendReply(session: RakNetSession) {
        session.sendWithoutQueue(buildPacket(31) { 
            writeUByte(ID_OPEN_CONNECTION_REPLY_2)
            writeFully(UNCONNECTED_CONSTANT)
            writeLong(session.binding.guid)
            writeSocketAddress(session.binding.address)
            writeUShort(session.mtu.toUShort())
            writeBoolean(false) // Security
        })
    }
}
