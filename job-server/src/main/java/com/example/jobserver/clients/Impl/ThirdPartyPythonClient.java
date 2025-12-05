package com.example.jobserver.clients.Impl;

import com.example.jobserver.clients.ExternalJobProcessor;
import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobResult;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import lombok.RequiredArgsConstructor;  

import lombok.extern.slf4j.Slf4j;


@Slf4j
@RequiredArgsConstructor
public class ThirdPartyPythonClient implements ExternalJobProcessor {

    private final WebClient client;
    private final String externalApiUrl;


    @Override
    public Future<JobResult> process(Job job) {
        JsonObject body = new JsonObject().put("jobId", job.getId());

        // Build full absolute URL and use postAbs()
        String fullUrl = externalApiUrl.endsWith("/") 
            ? externalApiUrl + "process" 
            : externalApiUrl + "/process";

        log.info("Calling external API: {} for job {}", fullUrl, job.getId());

        return client.postAbs(fullUrl)
            .sendJsonObject(body)
            .map(resp -> {
                if (resp.statusCode() >= 400) {
                    throw new RuntimeException("External API returned error: " + resp.statusCode() + " - " + resp.bodyAsString());
                }
                return new JobResult(resp.bodyAsJsonObject());
            })
            .onFailure(err -> log.error("Failed to process job {}: {}", job.getId(), err.getMessage()))
            .onSuccess(result -> log.info("Job {} processed successfully", job.getId()));
    }
}
