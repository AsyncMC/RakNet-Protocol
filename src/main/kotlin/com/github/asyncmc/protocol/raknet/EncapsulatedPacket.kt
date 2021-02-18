package com.github.asyncmc.protocol.raknet

import com.github.asyncmc.protocol.raknet.api.RakNetPriority
import com.github.asyncmc.protocol.raknet.api.RakNetReliability
import com.github.asyncmc.protocol.raknet.util.readUMediumLittleEndian
import com.github.asyncmc.protocol.raknet.util.writeUMediumLittleEndian
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import java.nio.ByteBuffer

/**
 * @author joserobjr
 * @since 2021-01-07
 */
@ExperimentalUnsignedTypes
data class EncapsulatedPacket (
    val content: Memory = Memory.Empty,
    val priority: RakNetPriority = RakNetPriority.MEDIUM,
    val reliability: RakNetReliability = RakNetReliability.UNRELIABLE,
    val reliabilityIndex: UInt = 0u,
    val sequenceIndex: UInt = 0u,
    val orderingIndex: UInt = 0u,
    val orderingChannel: UByte = 0u,
    val split: Boolean = false,
    val partCount: Int = 1,
    val partId: UShort = 0u,
    val partIndex: Int = 0
) {
    val size = reliability.size + content.size32 + if (split) 10 else 0 
    
    fun encode() = buildPacket(size) {
        val reliability = reliability
        val content = content
        val flags = (reliability.ordinal shl 5) or if (split) 0b00010000 else 0
        writeUByte(flags.toUByte())
        writeUShort((content.size32 shl 3).toUShort()) // Size

        if (reliability.reliable) {
            writeUMediumLittleEndian(reliabilityIndex)
        }

        if (reliability.sequenced) {
            writeUMediumLittleEndian(sequenceIndex)
        }

        if (reliability.ordered || reliability.sequenced) {
            writeUMediumLittleEndian(orderingIndex)
            writeUByte(orderingChannel)
        }

        if (split) {
            writeInt(partCount)
            writeUShort(partId)
            writeInt(partIndex)
        }

        writeFully(content, 0, content.size32)
    }
    
    companion object {
        fun decode(input: Input) = with(input) {
            val flags = readUByte().toInt()
            val reliability = RakNetReliability[flags and 0b11100000 shr 5]
            val split = (flags and 0b00010000) != 0
            val size = (readUShort().toInt() + 7) shl 3
            val buffer = ByteBuffer.allocate(size)
            EncapsulatedPacket(
                reliability = reliability,
                split = split,
                reliabilityIndex = if (reliability.reliable) readUMediumLittleEndian() else 0u,
                sequenceIndex = if (reliability.sequenced) readUMediumLittleEndian() else 0u,
                orderingIndex = if (reliability.ordered || reliability.sequenced) readUMediumLittleEndian() else 0u,
                orderingChannel = if (reliability.ordered || reliability.sequenced) readUByte() else 0u,
                partCount = if (split) readInt() else 0,
                partId = if (split) readUShort() else 0u,
                partIndex = if (split) readInt() else 0,
                content = buffer.also { readFully(it) }.let(Memory::of)
            )
        }
    }
    
}
