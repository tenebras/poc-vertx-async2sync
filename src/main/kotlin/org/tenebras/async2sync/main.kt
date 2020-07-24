package org.tenebras.async2sync

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.impl.VertxImpl
import io.vertx.ext.web.impl.RouterImpl
import io.vertx.kotlin.core.shareddata.getCounterAwait
import io.vertx.kotlin.core.shareddata.incrementAndGetAwait
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

suspend fun Vertx.nextHttpPortInCluster() = 8080 + sharedData().getCounterAwait("ports").incrementAndGetAwait().toInt()

fun main() {
    val options = VertxOptions()

    Vertx.clusteredVertx(options) { vertxResult ->
        if (vertxResult.succeeded()) {
            val vertx = vertxResult.result()
            val eventBus = vertx.eventBus()
            val router = RouterImpl(vertx)
            val clusterManager = (vertx as VertxImpl).clusterManager as HazelcastClusterManager

            // fixme: blocking call
            val waitingRequests = clusterManager.hazelcastInstance.getSet<String>("waiting")

            router.get("/callback").handler { ctx ->
                val id = ctx.queryParam("id").first()
                val value = ctx.queryParam("value").first()

                if (waitingRequests.contains(id)) {
                    eventBus.request<String>("hello.$id", value) {
                        ctx.response().end(it.result().body())
                    }
                } else {
                    ctx.response().end("No waiting request with this id")
                }
            }

            router.get("/hello").handler { ctx ->
                val id = ctx.queryParam("id").first()
                val consumer = eventBus.consumer<String>("hello.$id")
                // fixme: blocking call
                waitingRequests.add(id)

                consumer.handler {
                    println("Received response for $id")
                    ctx.response().end("Hello, ${it.body()}")
                    it.reply("sent")
                    // fixme: blocking call
                    waitingRequests.remove(id)
                    consumer.unregister()
                }

                ctx.vertx().setTimer(10000) {
                    consumer.unregister()
                    // fixme: blocking call
                    waitingRequests.remove(id)
                    ctx.fail(HttpResponseStatus.GATEWAY_TIMEOUT.code())
                }
            }

            router.get("/status").handler { ctx ->
                ctx
                    .response()
                    .putHeader("Content-Type", "application/json")
                    .end(
                        ObjectMapper().writeValueAsString(
                            mapOf(
                                "nodes" to clusterManager.nodes,
                                "idsWaitingForResponse" to waitingRequests
                            )
                        )
                    )
            }

            GlobalScope.launch(vertx.dispatcher()) {
                vertx
                    .createHttpServer()
                    .requestHandler(router)
                    .listen(vertx.nextHttpPortInCluster()) { listen ->
                        val httpPort = listen.result().actualPort()

                        if (listen.succeeded()) {
                            println("HTTP server running on port $httpPort")
                        } else {
                            println("Failed to start HTTP server on port $httpPort, '${listen.cause()}'")
                        }
                    }
            }
        } else {
            println("Failed to run clustered vertx: ${vertxResult.cause()}")
        }
    }
}