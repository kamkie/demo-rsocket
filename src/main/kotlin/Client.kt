import io.rsocket.AbstractRSocket
import io.rsocket.Payload
import io.rsocket.RSocketFactory
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.server.TcpServerTransport
import io.rsocket.util.DefaultPayload
import mu.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong


val logger = KotlinLogging.logger {}

val sch3 = Schedulers.newParallel("baz", 6)

fun main() {
    RSocketFactory.receive()
            .acceptor { setup, rs ->
                Mono.fromCallable {
                    object : AbstractRSocket() {
                        override fun requestStream(payload: Payload): Flux<Payload> {
                            logger.info { "new request ${payload.dataUtf8}" }
                            return Flux.range(0, 10_000_000)
//                                    .publishOn(sch3)
//                                    .subscribeOn(sch3)
                                    .map { DefaultPayload.create("Interval: $it") }
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

    logger.info { "first" }
    socket
            .requestStream(DefaultPayload.create("Hello 1"))
            .map { it.dataUtf8 }
            .doOnNext { count.incrementAndGet() }
            .subscribeOn(sch3)
            .subscribe()

    logger.info { "second" }
    socket
            .requestStream(DefaultPayload.create("Hello 2"))
            .map { it.dataUtf8 }
            .doOnNext { count.incrementAndGet() }
            .subscribeOn(sch3)
            .subscribe()

    logger.info { "third" }
    socket
            .requestStream(DefaultPayload.create("Hello 3"))
            .map { it.dataUtf8 }
            .doOnNext { count.incrementAndGet() }
            .subscribeOn(sch3)
            .subscribe()

    Flux.interval(Duration.ofSeconds(1))
            .doOnNext {
                val countInOneSecond = count.getAndSet(0)
                logger.info { "count in 1s: $countInOneSecond" }
            }
            .then().block()
}
