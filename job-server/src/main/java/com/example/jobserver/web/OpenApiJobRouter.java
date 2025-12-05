package com.example.jobserver.web;

import com.example.jobserver.model.Job;
import com.example.jobserver.service.JobService;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.openapi.RouterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenAPI 3-based router that generates routes from the OpenAPI specification.
 * Provides automatic request validation and documentation.
 */
@RequiredArgsConstructor
@Slf4j
public class OpenApiJobRouter {

    private final JobService jobService;

    /**
     * Creates a router from the OpenAPI specification.
     * 
     * @param vertx The Vertx instance
     * @return Future containing the configured router
     */
    public Future<Router> createRouter(Vertx vertx) {
        return RouterBuilder.create(vertx, "openapi.yaml")
            .map(routerBuilder -> {
                // Configure operation handlers
                configureOperations(routerBuilder);
                
                // Build and return the router
                Router router = routerBuilder.createRouter();
                log.info("OpenAPI router created successfully");
                return router;
            })
            .onFailure(err -> log.error("Failed to create OpenAPI router", err));
    }

    private void configureOperations(RouterBuilder routerBuilder) {
        // POST /jobs - Submit a new job
        routerBuilder.operation("submitJob")
            .handler(ctx -> {
                JsonObject body = ctx.body().asJsonObject();
                
                long userId = body.getLong("userId");
                Long projectId = body.getLong("projectId");
                JsonObject params = body.getJsonObject("parameters", new JsonObject());

                jobService.submitJob(userId, projectId, params)
                    .onSuccess(job -> {
                        JsonObject response = new JsonObject()
                                .put("jobId", job.getId())
                                .put("status", job.getStatus().name());
                        ctx.response()
                            .setStatusCode(202)
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                    })
                    .onFailure(err -> {
                        if (err instanceof IllegalArgumentException) {
                            ctx.response()
                                .setStatusCode(400)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("error", err.getMessage()).encode());
                        } else {
                            log.error("Error submitting job", err);
                            ctx.response()
                                .setStatusCode(500)
                                .putHeader("Content-Type", "application/json")
                                .end(new JsonObject().put("error", "Internal server error").encode());
                        }
                    });
            });

        // GET /jobs/{jobId} - Get job details
        routerBuilder.operation("getJob")
            .handler(ctx -> {
                String jobId = ctx.pathParam("jobId");

                jobService.getJob(jobId)
                    .onSuccess(jobOpt -> {
                        if (jobOpt.isEmpty()) {
                            ctx.response().setStatusCode(404).end();
                            return;
                        }

                        Job job = jobOpt.get();
                        JsonObject response = new JsonObject()
                                .put("jobId", job.getId())
                                .put("status", job.getStatus().name())
                                .put("userId", job.getUserId())
                                .put("projectId", job.getProjectId())
                                .put("parameters", job.getParameters())
                                .put("result", job.getResult() != null ? job.getResult().payload() : null)
                                .put("error", job.getErrorMessage());

                        ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                    })
                    .onFailure(err -> {
                        log.error("Error getting job", err);
                        ctx.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Internal server error").encode());
                    });
            });

        // GET /jobs/user/{userId} - Get jobs by user
        routerBuilder.operation("getJobsByUser")
            .handler(ctx -> {
                long userId = Long.parseLong(ctx.pathParam("userId"));

                jobService.getJobsByUser(userId)
                    .onSuccess(jobs -> {
                        JsonArray arr = new JsonArray();
                        for (Job job : jobs) {
                            arr.add(new JsonObject()
                                    .put("jobId", job.getId())
                                    .put("status", job.getStatus().name())
                                    .put("userId", job.getUserId())
                                    .put("projectId", job.getProjectId())
                                    .put("createdAt", job.getCreatedAt().toString()));
                        }
                        ctx.response()
                            .setStatusCode(200)
                            .putHeader("Content-Type", "application/json")
                            .end(arr.encode());
                    })
                    .onFailure(err -> {
                        log.error("Error getting jobs by user", err);
                        ctx.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject().put("error", "Internal server error").encode());
                    });
            });

        log.info("OpenAPI operations configured: submitJob, getJob, getJobsByUser");
    }
}

