import com.github.asyncmc.protocol.raknet.api.RakNetAPI;
import com.github.asyncmc.protocol.raknet.asyncmc.RakNetAsyncMC;

module com.github.asyncmc.protocol.raknet.asyncmc {
    requires kotlin.stdlib;
    requires ktor.network;
    requires kotlinx.coroutines.core;
    requires ktor.utils.jvm;
    requires ktor.io.jvm;
    requires jctools.core;

    requires com.github.asyncmc.protocol.raknet.api;
    provides RakNetAPI with RakNetAsyncMC;

    exports com.github.asyncmc.protocol.raknet.asyncmc;
}
