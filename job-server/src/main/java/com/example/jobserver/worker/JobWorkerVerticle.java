package com.example.jobserver.worker;

import com.example.jobserver.clients.ExternalJobProcessor;
import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobStatus;
import com.example.jobserver.repositories.JobRepository;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

/**
 * Worker Verticle for processing jobs asynchronously.
 * Uses fire-and-forget pattern - job status is tracked via DB updates.
 */
@Slf4j
public class JobWorkerVerticle extends AbstractVerticle {

    public static final String JOB_PROCESS_ADDRESS = "job.process";

    private final JobRepository jobRepository;
    private final ExternalJobProcessor externalProcessor;

    public JobWorkerVerticle(JobRepository jobRepository, ExternalJobProcessor externalProcessor) {
        this.jobRepository = jobRepository;
        this.externalProcessor = externalProcessor;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        // Listen for job processing messages on the event bus (fire-and-forget)
        vertx.eventBus().<JsonObject>consumer(JOB_PROCESS_ADDRESS, message -> {
            String jobId = message.body().getString("jobId");
            log.info("Worker received job: {}", jobId);
            processJob(jobId);
        });

        log.info("JobWorkerVerticle started");
        startPromise.complete();
    }

    private void processJob(String jobId) {
        // Clean Future chain using compose() - no callback hell
        jobRepository.updateStatus(jobId, JobStatus.PROCESSING)
            .compose(v -> jobRepository.findById(jobId))
            .compose(jobOpt -> {
                if (jobOpt.isEmpty()) {
                    return Future.failedFuture("Job not found: " + jobId);
                }
                Job job = jobOpt.get();
                log.info("Processing job {} with external service", jobId);
                return externalProcessor.process(job);
            })
            .compose(result -> {
                log.info("Job {} processed, saving result", jobId);
                return jobRepository.updateResult(jobId, result);
            })
            .compose(v -> jobRepository.updateStatus(jobId, JobStatus.COMPLETED))
            .onSuccess(v -> log.info("Job {} completed successfully", jobId))
            .onFailure(err -> failJob(jobId, err.getMessage()));
    }

    private void failJob(String jobId, String errorMessage) {
        log.error("Job {} failed: {}", jobId, errorMessage);
        jobRepository.updateFailure(jobId, errorMessage)
            .compose(v -> jobRepository.updateStatus(jobId, JobStatus.FAILED))
            .onFailure(err -> log.error("Failed to update failure status for job {}: {}", jobId, err.getMessage()));
    }
}
