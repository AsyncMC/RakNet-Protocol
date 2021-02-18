package com.github.asyncmc.protocol.raknet.internal

import java.util.concurrent.TimeUnit
import kotlin.time.AbstractLongTimeSource
import kotlin.time.ExperimentalTime

/**
 * @author joserobjr
 * @since 2021-01-07
 */
@ExperimentalTime
internal class CachedNanoTimeSource: AbstractLongTimeSource(TimeUnit.NANOSECONDS) {
    private var currentTime = System.nanoTime()
    
    fun updateTime() {
        currentTime = System.nanoTime()
    }
    
    override fun read() = currentTime

    override fun toString(): String {
        return "CachedNanoTimeSource($currentTime)"
    }
}
