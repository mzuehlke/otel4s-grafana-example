import cats.effect.std.Random
import cats.effect.*
import com.comcast.ip4s.{ipv4, port}
import io.opentelemetry.api.GlobalOpenTelemetry
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer

object Server extends IOApp {
  private def app[F[_]: Async: LiftIO]: Resource[F, Server] =
    for {
      given Random[F] <- Resource.eval(Random.scalaUtilRandom[F])
      otel <- Resource
        .eval(Async[F].delay(GlobalOpenTelemetry.get))
        .evalMap(OtelJava.forAsync[F])
      serviceName = "otel4s-grafana-example"
      given Meter[F] <- Resource.eval(otel.meterProvider.get(serviceName))
      given Tracer[F] <- Resource.eval(otel.tracerProvider.get(serviceName))
      exampleService = ExampleService[F](50, 500, 20)
      route = ExampleRoute[F](exampleService)
      server <- EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(route.orNotFound.traced)
        .build
    } yield server

  override def run(args: List[String]): IO[ExitCode] =
    app[IO].useForever.as(ExitCode.Success)
}