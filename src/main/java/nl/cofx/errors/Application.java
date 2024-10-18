package nl.cofx.errors;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    private static final String CONTENT_TYPE = "content-type";
    private static final String APPLICATION_JSON = "application/json";

    private final Vertx vertx;

    public Application(Vertx vertx) {
        this.vertx = vertx;
    }

    public static void main(String[] args) {
        var application = new Application(Vertx.vertx());
        application.start().onComplete(
                result -> log.info("Application started successfully"),
                result -> log.error("Application failed to start", result.getCause()));
    }

    public Future<Void> start() {
        return Future.all(startFirstServer(), startSecondServer()).mapEmpty();
    }

    private Future<Void> startFirstServer() {
        var router = Router.router(vertx);

        router.route("/failWithStatusCode").handler(Application::trigger400);
        router.errorHandler(400, Application::respondWith400);

        return startServer(8080, router);
    }

    private Future<Void> startSecondServer() {
        var router = Router.router(vertx);

        router.route("/failWithStatusCode").handler(Application::trigger400);
        router.errorHandler(400, Application::respondWith400);
        router.route().failureHandler(routingContext -> {
            log.info("Failure handler for status code {}", routingContext.statusCode(), routingContext.failure());
            routingContext.response()
                    .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setStatusCode(routingContext.statusCode())
                    .end(new JsonObject().put("error", routingContext.statusCode()).toBuffer());
        });

        return startServer(8081, router);
    }

    private static void respondWith400(RoutingContext routingContext) {
        log.info("Returning response for 400");
        routingContext.response()
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Bad request").toBuffer());
    }

    private static void trigger400(RoutingContext routingContext) {
        log.info("Failing with status code");
        routingContext.fail(400);
    }

    private Future<Void> startServer(int port, Router router) {
        var promise = Promise.<Void>promise();

        var httpServer = vertx.createHttpServer();
        httpServer.requestHandler(router);
        httpServer.listen(port, asyncServer -> {
            if (asyncServer.succeeded()) {
                log.info("Listening for HTTP requests on port {}", port);
                promise.complete();
            } else {
                log.error("Failed to listen for HTTP requests on port {}", port, asyncServer.cause());
                promise.fail(asyncServer.cause());
            }
        });

        return promise.future();
    }
}
