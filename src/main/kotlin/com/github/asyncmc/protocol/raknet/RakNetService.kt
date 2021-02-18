package com.github.asyncmc.protocol.raknet

import com.github.asyncmc.protocol.raknet.api.RakNetAPI
import com.github.asyncmc.protocol.raknet.api.RakNetConfig
import com.github.asyncmc.protocol.raknet.api.RakNetServer

/**
 * @author joserobjr
 * @since 2021-01-06
 */
class RakNetService: RakNetAPI {
    override val name: String
        get() = "PowerNukkit RakNet"

    override fun openServer(config: RakNetConfig): RakNetServer {
        return RakNetServer {
            socketAddresses += config.socketAddresses
            listeners = config.listeners.copy()
            parentJob = config.parentJob
            supportedProtocols += config.supportedProtocols
            maxConnections = config.maxConnections
        }
    }
}
