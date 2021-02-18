import com.github.asyncmc.protocol.raknet.api.RakNetAPI;

module com.github.asyncmc.protocol.raknet {
    requires ktor.network.jvm;
    requires kotlinx.coroutines.core.jvm;
    requires ktor.utils.jvm;
    requires ktor.io.jvm;
    requires jctools.core;
    requires kotlin.inline.logger.jvm;
    requires kotlin.stdlib;
    requires kotlin.stdlib.common;
    requires kotlin.stdlib.jdk7;
    requires kotlin.stdlib.jdk8;
    
    exports com.github.asyncmc.protocol.raknet;
    exports com.github.asyncmc.protocol.raknet.listener;
    exports com.github.asyncmc.protocol.raknet.packet;
    exports com.github.asyncmc.protocol.raknet.util;
    
    requires com.github.asyncmc.protocol.raknet.api;
    requires atomicfu.jvm;
    provides RakNetAPI with com.github.asyncmc.protocol.raknet.RakNetService;
}
