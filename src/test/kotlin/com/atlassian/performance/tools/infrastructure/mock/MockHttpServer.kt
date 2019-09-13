package com.atlassian.performance.tools.infrastructure.mock

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class MockHttpServer {

    internal fun start(): CloseableHttpServer {
        val executorService = Executors.newCachedThreadPool()
        val server = startHttpServer(executorService)
        return CloseableHttpServer(server, executorService)
    }

    private fun startHttpServer(executor: Executor): HttpServer {
        val httpServer = HttpServer.create(InetSocketAddress(0), 0)
        httpServer.executor = executor
        httpServer.start()
        return httpServer
    }

    internal interface RequestHandler : HttpHandler {
        fun getContext(): String
    }

    class CloseableHttpServer(
        private val httpServer: HttpServer,
        private val executorService: ExecutorService
    ) : AutoCloseable {

        override fun close() {
            executorService.shutdownNow()
            httpServer.stop(60)
        }

        fun register(handler: RequestHandler): URI {
            httpServer.createContext(handler.getContext()).handler = handler
            val port = httpServer.address.port
            return URI("http://localhost:$port${handler.getContext()}")
        }
    }
}