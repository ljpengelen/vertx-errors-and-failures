package nl.cofx.errors;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.vertx.core.http.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(VertxExtension.class)
@Slf4j
class ApplicationTest {

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        var application = new Application(vertx);
        application.start().onComplete(vertxTestContext.succeedingThenComplete());
    }

    @Test
    void returns400CreatedByErrorHandler_givenRequestToFailingEndpoint(Vertx vertx, VertxTestContext vertxTestContext) {
        var httpClient = vertx.createHttpClient();
        httpClient.request(GET, 8080, "localhost", "/failWithStatusCode")
                .flatMap(HttpClientRequest::send)
                .flatMap(response -> {
                    assertThat(response.statusCode()).isEqualTo(400);
                    return response.body();
                }).map(body -> {
                    assertThat(body.toJsonObject()).isEqualTo(new JsonObject()
                            .put("error", "Bad request")
                            .put("source", "errorHandler"));
                    return null;
                }).onComplete(vertxTestContext.succeedingThenComplete());
    }

    @Test
    void returns400CreatedByFailureHandler_givenRequestToFailingEndpoint(Vertx vertx, VertxTestContext vertxTestContext) {
        var httpClient = vertx.createHttpClient();
        httpClient.request(GET, 8081, "localhost", "/failWithStatusCode")
                .flatMap(HttpClientRequest::send)
                .flatMap(response -> {
                    assertThat(response.statusCode()).isEqualTo(400);
                    return response.body();
                }).map(body -> {
                    assertThat(body.toJsonObject()).isEqualTo(new JsonObject()
                            .put("error", 400)
                            .put("source", "failureHandler"));
                    return null;
                }).onComplete(vertxTestContext.succeedingThenComplete());
    }

    @Test
    void returns400CreatedByErrorHandler_givenHttpRequestWithoutHostHeader(Vertx vertx, VertxTestContext vertxTestContext) {
        var netClient = vertx.createNetClient();
        netClient.connect(8080, "localhost")
                .map(socket -> {
                    socket.handler(buffer -> {
                        log.info("Received {}", buffer);

                        var string = buffer.toString();
                        vertxTestContext.verify(() -> {
                            assertThat(string).startsWith("HTTP/1.1 400 Bad Request");
                            assertThat(string).contains("{\"error\":\"Bad request\",\"source\":\"errorHandler\"}");
                        });

                        vertxTestContext.completeNow();
                    });

                    socket.write("GET / HTTP/1.1\n\n");

                    return null;
                }).onFailure(vertxTestContext::failNow);
    }

    @Test
    void returns400CreatedByFailureHandler_givenHttpRequestWithoutHostHeader(Vertx vertx, VertxTestContext vertxTestContext) {
        var netClient = vertx.createNetClient();
        netClient.connect(8081, "localhost")
                .map(socket -> {
                    socket.handler(buffer -> {
                        log.info("Received {}", buffer);

                        var string = buffer.toString();
                        vertxTestContext.verify(() -> {
                            assertThat(string).startsWith("HTTP/1.1 400 Bad Request");
                            assertThat(string).contains("{\"error\":400,\"source\":\"failureHandler\"}");
                        });

                        vertxTestContext.completeNow();
                    });

                    socket.write("GET / HTTP/1.1\n\n");

                    return null;
                }).onFailure(vertxTestContext::failNow);
    }
}
