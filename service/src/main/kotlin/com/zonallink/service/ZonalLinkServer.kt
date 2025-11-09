package com.zonallink.service

import com.zonallink.core.ZonalLinkEngine
import com.zonallink.core.ZonalLinkConfig
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.slf4j.LoggerFactory

class ZonalLinkServer(
    private val port: Int = 7575,
    config: ZonalLinkConfig = ZonalLinkConfig(),
    private val engine: ZonalLinkEngine = ZonalLinkEngine(config = config)
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var server: Server? = null

    fun start(): ZonalLinkServer {
        server = NettyServerBuilder
            .forPort(port)
            .addService(ZonalIngestionGrpcService(engine))
            .addService(VehicleTelemetryGrpcService(engine))
            .addService(VehicleCommandGrpcService(engine))
            .build()
            .start()

        logger.info("ZonalLink gRPC server listening on {}", port)
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
        return this
    }

    fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    fun stop() {
        server?.shutdown()
        server = null
    }
}
