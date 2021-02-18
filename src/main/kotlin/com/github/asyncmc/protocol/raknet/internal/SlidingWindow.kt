package com.github.asyncmc.protocol.raknet.internal

import com.github.asyncmc.protocol.raknet.util.squared
import kotlin.time.*

/**
 * @author joserobjr
 * @since 2021-01-07
 */
@OptIn(ExperimentalTime::class)
internal class SlidingWindow (
    private val timeSource: TimeSource,
    val maximumTransferUnit: Int,
    private var congestionWindow: Double = maximumTransferUnit.toDouble(),
    private val maximumThreshold: Duration = 2.seconds,
    private val additionalVariance: Duration = 30.milliseconds,
    private val acknowledgementSendDelay: Duration = 10.milliseconds
) {
    private var ssThresh: Double = 0.0
    var estimatedRoundTripTime: Duration? = null; private set
    private var lastRoundTripTime: Duration? = null
    private var deviationRoundTripTime = Duration.ZERO
    private var oldestUnsentAcknowledgment: TimeMark? = null
    private var nextCongestionControlBlock = 0L
    private var backoffThisBlock = false
    
    val isInSlowStart get() = congestionWindow <= ssThresh || ssThresh == 0.0
    
    fun retransmissionBandwidth(negativeAcknowledgmentBytes: Int) = negativeAcknowledgmentBytes
    
    fun transmissionBandwidth(negativeAcknowledgmentBytes: Int) =
        (congestionWindow - negativeAcknowledgmentBytes).coerceAtLeast(0.0).toInt()
    
    fun packetReceived(time: TimeMark) {
        if (oldestUnsentAcknowledgment == null) {
            oldestUnsentAcknowledgment = time
        }
    }
    
    fun packetResent(currentSequenceIndex: Long) {
        if (!backoffThisBlock && congestionWindow > maximumTransferUnit * 2) {
            ssThresh = congestionWindow / 2
            
            if (ssThresh < maximumTransferUnit) {
                ssThresh = maximumTransferUnit.toDouble()
            }
            congestionWindow = maximumTransferUnit.toDouble()
            
            nextCongestionControlBlock = currentSequenceIndex
            backoffThisBlock = true
        }
    }
    
    fun negativeAcknowledgementReceived() {
        if (!backoffThisBlock) {
            ssThresh = congestionWindow / 2.0
        }
    }
    
    fun acknowledgementReceived(roundTripTime: Duration, sequenceIndex: Long, currentSequenceIndex: Long) {
        lastRoundTripTime = roundTripTime
        
        val estimatedRoundTripTime = estimatedRoundTripTime
        if (estimatedRoundTripTime == null) {
            this.estimatedRoundTripTime = roundTripTime
            this.deviationRoundTripTime = roundTripTime
        } else {
            val difference = roundTripTime - estimatedRoundTripTime
            this.estimatedRoundTripTime = estimatedRoundTripTime + 0.5 * difference
            this.deviationRoundTripTime += 0.5 * (difference.absoluteValue - deviationRoundTripTime)
        }
        
        val isNewCongestionControlPeriod = sequenceIndex > nextCongestionControlBlock
        
        if (isNewCongestionControlPeriod) {
            backoffThisBlock = false
            nextCongestionControlBlock = currentSequenceIndex
        }
        
        if (isInSlowStart) {
            congestionWindow += maximumTransferUnit
            
            if (congestionWindow > ssThresh && ssThresh != 0.0) {
                congestionWindow = ssThresh + maximumTransferUnit.squared() / congestionWindow
            }
        } else if (isNewCongestionControlPeriod) {
            congestionWindow += maximumTransferUnit.squared() / congestionWindow
        }
    }
    
    fun acknowledgementSent() {
        oldestUnsentAcknowledgment = null
    }
    
    fun rtoForRetransmission(): Duration {
        val estimatedRoundTripTime = estimatedRoundTripTime ?: return maximumThreshold
        
        val threshold  = (2.0 * estimatedRoundTripTime + 4.0 * deviationRoundTripTime) + additionalVariance
        return threshold.coerceAtMost(maximumThreshold)
    }
    
    fun shouldSendAcknowledgements(): Boolean {
        if (lastRoundTripTime == null) {
            return true
        }
        
        val oldestUnsentAcknowledgment = oldestUnsentAcknowledgment ?: return true
        return (oldestUnsentAcknowledgment + acknowledgementSendDelay).hasPassedNow()
    }
}
