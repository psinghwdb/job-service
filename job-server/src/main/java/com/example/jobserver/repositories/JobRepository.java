package com.example.jobserver.repositories;

import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;

import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobResult;
import com.example.jobserver.model.JobStatus;

public interface JobRepository {
    Future<Job> save(Job job);
    Future<Optional<Job>> findById(String jobId);
    Future<List<Job>> findByUserId(long userId);
    Future<Void> updateStatus(String jobId, JobStatus status);
    Future<Void> updateResult(String jobId, JobResult result);
    Future<Void> updateFailure(String jobId, String errorMessage);
}
