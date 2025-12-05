package com.example.jobserver.service.impl;

import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobStatus;
import com.example.jobserver.repositories.JobRepository;
import com.example.jobserver.repositories.ProjectRepository;
import com.example.jobserver.repositories.UserRepository;
import com.example.jobserver.service.JobService;
import com.example.jobserver.worker.JobWorkerVerticle;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final EventBus eventBus;

    @Override
    public Future<Job> submitJob(long userId, Long projectId, JsonObject params) {
        // Validate user exists (async)
        return userRepository.existsById(userId)
            .compose(userExists -> {
                if (!userExists) {
                    return Future.failedFuture(new IllegalArgumentException("User not found: " + userId));
                }
                // Validate project if provided (async)
                if (projectId != null) {
                    return projectRepository.existsById(projectId)
                        .compose(projectExists -> {
                            if (!projectExists) {
                                return Future.failedFuture(new IllegalArgumentException("Project not found: " + projectId));
                            }
                            return createAndSaveJob(userId, projectId, params);
                        });
                }
                return createAndSaveJob(userId, projectId, params);
            });
    }

    private Future<Job> createAndSaveJob(long userId, Long projectId, JsonObject params) {
        Job job = Job.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .projectId(projectId)
            .parameters(params)
            .status(JobStatus.PENDING)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        return jobRepository.save(job)
            .onSuccess(savedJob -> {
                JsonObject message = new JsonObject().put("jobId", job.getId());
                log.info("Sending job {} to worker", job.getId());
                eventBus.send(JobWorkerVerticle.JOB_PROCESS_ADDRESS, message);
                log.info("Job {} sent to worker", job.getId());
            });
    }

    @Override
    public Future<Optional<Job>> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    public Future<List<Job>> getJobsByUser(long userId) {
        return jobRepository.findByUserId(userId);
    }
}
