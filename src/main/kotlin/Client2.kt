package com.example.demo.rsocket2

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.rsocket.kotlin.*
import io.rsocket.kotlin.transport.netty.client.TcpClientTransport
import io.rsocket.kotlin.transport.netty.server.NettyContextCloseable
import io.rsocket.kotlin.transport.netty.server.TcpServerTransport
import io.rsocket.kotlin.util.AbstractRSocket
import mu.KotlinLogging
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


val logger = KotlinLogging.logger {}

val clientScheduler = Schedulers.single()
val serverScheduler = Schedulers.from(ThreadPoolExecutor(4, 4, 100, TimeUnit.SECONDS, ArrayBlockingQueue(10_000_000)))

fun main() {
    val rSocket: Single<RSocket> = RSocketFactory               // Requester RSocket
            .connect()
            .acceptor { { requesterRSocket -> handlerClient(requesterRSocket) } }  // Optional handlerClient RSocket
            .transport(TcpClientTransport.create("localhost", 7000))
            .start()
    rSocket.subscribe { socket -> socket.fireAndForget(DefaultPayload.text("client handlerClient response")) }

    val closeable= RSocketFactory
            .receive()
            .acceptor { { setup, rSocket -> handlerServer(setup, rSocket) } } // server handlerClient RSocket
            .transport(TcpServerTransport.create(7000))
            .start()
            .subscribe()




    Thread.currentThread().join()
}

fun handlerClient(requester: RSocket): RSocket {
    return object : AbstractRSocket() {
        override fun requestStream(payload: Payload): Flowable<Payload> {
            return Flowable.just(DefaultPayload.text("client handlerClient response"))
        }
    }
}

private fun handlerServer(setup: Setup, rSocket: RSocket): Single<RSocket> {
    return Single.just(object : AbstractRSocket() {
        override fun requestStream(payload: Payload): Flowable<Payload> {
            return Flowable.just(DefaultPayload.text("server handlerClient response"))
        }
    })
}

