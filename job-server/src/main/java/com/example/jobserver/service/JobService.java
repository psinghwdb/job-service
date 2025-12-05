package com.example.jobserver.service;

import com.example.jobserver.model.Job;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Optional;

public interface JobService {
    Future<Job> submitJob(long userId, Long projectId, JsonObject params);
    Future<Optional<Job>> getJob(String jobId);
    Future<List<Job>> getJobsByUser(long userId);
}
