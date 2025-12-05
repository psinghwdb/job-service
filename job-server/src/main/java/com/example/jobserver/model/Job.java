package com.example.jobserver.model;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@AllArgsConstructor
@SuperBuilder
public class Job {

    private final String id;
    private final long userId;
    private final Long projectId; // nullable
    private JobStatus status;
    private final JsonObject parameters;
    private JobResult result;
    private String errorMessage;
    private final Instant createdAt;
    private Instant updatedAt;

}
