package com.example.jobserver.clients;

import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobResult;

import io.vertx.core.Future;

public interface ExternalJobProcessor {
    Future<JobResult> process(Job job);
}