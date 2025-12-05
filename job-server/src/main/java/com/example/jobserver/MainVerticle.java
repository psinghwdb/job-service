package com.example.jobserver;

import com.example.jobserver.config.AppConfig;
import com.example.jobserver.config.AppModule;
import com.example.jobserver.web.OpenApiJobRouter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application verticle - responsible only for startup orchestration.
 * All dependency creation is delegated to AppModule.
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        // 1. Load configuration
        AppConfig config = AppConfig.fromEnvironment();
        
        // 2. Initialize all dependencies
        AppModule appModule = new AppModule(vertx, config);

        // 3. Deploy Worker Verticles (runs on worker thread pool)
        DeploymentOptions workerOptions = new DeploymentOptions()
            .setThreadingModel(ThreadingModel.WORKER)
            .setInstances(config.getWorkerInstances());

        vertx.deployVerticle(appModule::createWorkerVerticle, workerOptions, ar -> {
            if (ar.succeeded()) {
                log.info("Deployed {} JobWorkerVerticle instances: {}", config.getWorkerInstances(), ar.result());
            } else {
                log.error("Failed to deploy JobWorkerVerticle", ar.cause());
            }
        });

        // 4. Create OpenAPI router and start HTTP server
        OpenApiJobRouter openApiRouter = new OpenApiJobRouter(appModule.getJobService());

        openApiRouter.createRouter(vertx)
            .onSuccess(apiRouter -> startHttpServer(config, apiRouter, startPromise))
            .onFailure(err -> {
                log.error("Failed to create OpenAPI router", err);
                startPromise.fail(err);
            });
    }

    private void startHttpServer(AppConfig config, Router apiRouter, Promise<Void> startPromise) {
        Router mainRouter = Router.router(vertx);

        // Body handler for all routes
        mainRouter.route().handler(BodyHandler.create());

        // Swagger UI - accessible at /docs or /swagger
        mainRouter.get("/docs").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "text/html")
                .sendFile("webroot/swagger-ui.html");
        });
        mainRouter.get("/swagger").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "text/html")
                .sendFile("webroot/swagger-ui.html");
        });

        // Serve OpenAPI spec at /api/openapi.yaml
        mainRouter.get("/api/openapi.yaml").handler(ctx -> {
            ctx.response()
                .putHeader("Content-Type", "application/x-yaml")
                .putHeader("Access-Control-Allow-Origin", "*")
                .sendFile("openapi.yaml");
        });

        // Mount OpenAPI routes at root (paths defined in openapi.yaml)
        mainRouter.route("/*").subRouter(apiRouter);

        // Static files (UI)
        mainRouter.route().handler(StaticHandler.create("webroot"));

        // Start HTTP server
        vertx.createHttpServer()
            .requestHandler(mainRouter)
            .listen(config.getHttpPort(), httpAr -> {
                if (httpAr.succeeded()) {
                    log.info("HTTP server started on port {}", config.getHttpPort());
                    log.info("===========================================");
                    log.info("App UI:        http://localhost:{}/", config.getHttpPort());
                    log.info("Swagger UI:    http://localhost:{}/docs", config.getHttpPort());
                    log.info("OpenAPI Spec:  http://localhost:{}/api/openapi.yaml", config.getHttpPort());
                    log.info("===========================================");
                    startPromise.complete();
                } else {
                    startPromise.fail(httpAr.cause());
                }
            });
    }
}
