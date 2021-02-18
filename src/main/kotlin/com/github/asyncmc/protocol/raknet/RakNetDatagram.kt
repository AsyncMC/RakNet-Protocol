package com.github.asyncmc.protocol.raknet

import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark

/**
 * @author joserobjr
 * @since 2021-01-07
 */
@ExperimentalTime
@ExperimentalUnsignedTypes
data class RakNetDatagram(
    val packets: List<EncapsulatedPacket>,
    val creationTime: TimeMark,
    val flags: UByte = FLAG_VALID,
    var nextSend: TimeMark? = null,
) {
    companion object {
        const val FLAG_VALID: UByte = 0b10000000u
        const val FLAG_ACK: UByte = 0b01000000u
        const val FLAG_HAS_B_AND_AS: UByte = 0b00100000u
        const val FLAG_NACK: UByte = 0b00100000u
        const val FLAG_PACKET_PAIR: UByte = 0b00010000u
        const val FLAG_CONTINUOUS_SEND: UByte = 0b00001000u
        const val FLAG_NEEDS_B_AND_AS: UByte = 0b00000100u
    }
}
