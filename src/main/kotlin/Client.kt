package com.example.demo.rsocket

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.UnicastSubject
import io.rsocket.kotlin.DefaultPayload
import io.rsocket.kotlin.Payload
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketFactory
import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport
import io.rsocket.kotlin.util.AbstractRSocket
import mu.KotlinLogging
import org.reactivestreams.Publisher
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


val logger = KotlinLogging.logger {}

val clientScheduler = Schedulers.single()
val serverScheduler = Schedulers.from(ThreadPoolExecutor(4, 4, 100, TimeUnit.SECONDS, ArrayBlockingQueue(10_000_000)))

fun main() {
    val serverSocket: RSocket = object : AbstractRSocket() {
        override fun requestChannel(payloads: Publisher<Payload>): Flowable<Payload> {
            return Flowable.fromPublisher(payloads)
                    .map { it.dataUtf8 }
                    .doOnNext { logger.info { "incoming message $it" } }
                    .flatMap { Flowable.range(0, 10_000_000) }
                    .map { DefaultPayload.text("Interval: $it") }
                    .subscribeOn(serverScheduler)
        }
    }

    RSocketFactory.receive()
            .acceptor { { setup, rSocket -> Single.just(serverSocket) } }
            .transport(TcpServerTransport.create("localhost", 7000))
            .start()
            .subscribe()

    val socket = RSocketFactory.connect()
            .transport(TcpClientTransport.create("localhost", 7000))
            .start().blockingGet()

    val count = AtomicLong(0)

    Observable.interval(1, TimeUnit.SECONDS)
            .doOnNext {
                val countInOneSecond = count.getAndSet(0)
                if (countInOneSecond > 0) {
                    logger.info { "count in 1s: $countInOneSecond" }
                }
            }
            .subscribe()

    val publishSubject = UnicastSubject.create<Payload>()
    socket
            .requestChannel(publishSubject.toFlowable(BackpressureStrategy.BUFFER))
            .map { it.dataUtf8 }
            .doOnSubscribe { logger.info { "${"first"} started" } }
            .doOnNext { count.incrementAndGet() }
            .doOnError { logger.info { "${"first"} error" } }
            .doOnComplete { logger.info { "${"first"} completed" } }
            .doOnCancel { logger.info { "${"first"} canceled" } }
            .subscribeOn(clientScheduler)
            .subscribe()

    publishSubject.onNext(DefaultPayload.text("first"))
    publishSubject.onNext(DefaultPayload.text("second"))
    publishSubject.onNext(DefaultPayload.text("third"))

    Thread.currentThread().join()
}

