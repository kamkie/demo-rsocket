package com.example.demo.rsocket

import io.rsocket.AbstractRSocket
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.RSocketFactory
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import mu.KotlinLogging
import org.reactivestreams.Publisher
import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong


val logger = KotlinLogging.logger {}

val clientScheduler = Schedulers.newParallel("clientScheduler", 1)
val serverScheduler = Schedulers.newParallel("serverScheduler", 2)
val serverScheduler2 = Schedulers.newParallel("serverScheduler", 2)

fun main() {
    RSocketFactory.receive()
            .acceptor { setup, rs ->
                Mono.fromCallable {
                    object : AbstractRSocket() {
                        override fun requestChannel(payload: Publisher<Payload>): Flux<Payload> {
                            return Flux.from(payload)
                                    .map { it.dataUtf8 }
                                    .doOnNext { logger.info { "incoming message $it" } }
                                    .flatMap { Flux.range(0, 10_000_000) }
                                    .map { DefaultPayload.create("Interval: $it") }
//                                    .publishOn(serverScheduler2)
                                    .subscribeOn(serverScheduler)
//                                    .onBackpressureDrop()
                        }
                    }
                }
            }
            .transport(TcpServerTransport.create("localhost", 7000))
            .start()
            .subscribe()

    val socket = RSocketFactory.connect()
            .transport(TcpClientTransport.create("localhost", 7000))
            .start()
            .block() ?: throw IllegalStateException("connection error")

    val count = AtomicLong(0)

    Flux.interval(Duration.ofSeconds(1))
            .doOnNext {
                val countInOneSecond = count.getAndSet(0)
                logger.info { "count in 1s: $countInOneSecond" }
            }
            .then().subscribe()

    val sink = sendRequest(socket, count, "first")

    sink.next(DefaultPayload.create("first"))
    sink.next(DefaultPayload.create("second"))
    sink.next(DefaultPayload.create("third"))

    Thread.currentThread().join()
}

private fun sendRequest(socket: RSocket, count: AtomicLong, rid: String): FluxSink<Payload> {
    logger.info { rid }
    val emitterProcessor = EmitterProcessor.create<Payload>(100_000)
    val sink = emitterProcessor.sink(FluxSink.OverflowStrategy.LATEST)

    socket
            .requestChannel(emitterProcessor)
            .map { it.dataUtf8 }
            .doOnSubscribe { logger.info { "$rid started" } }
            .doOnNext { count.incrementAndGet() }
            .doOnError { logger.info { "$rid error" } }
            .doOnComplete { logger.info { "$rid completed" } }
            .doOnCancel { logger.info { "$rid canceled" } }
            .subscribeOn(clientScheduler)
            .subscribe()

    return sink
}
