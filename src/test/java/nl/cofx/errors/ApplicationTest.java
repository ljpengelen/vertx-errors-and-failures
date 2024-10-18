package nl.cofx.errors;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class ApplicationTest {

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        var application = new Application(vertx);
        application.start().onComplete(vertxTestContext.succeedingThenComplete());
    }
}
