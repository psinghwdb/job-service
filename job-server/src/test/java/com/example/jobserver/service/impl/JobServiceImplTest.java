package com.example.jobserver.service.impl;

import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobStatus;
import com.example.jobserver.repositories.JobRepository;
import com.example.jobserver.repositories.ProjectRepository;
import com.example.jobserver.repositories.UserRepository;
import com.example.jobserver.worker.JobWorkerVerticle;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class JobServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EventBus eventBus;

    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobServiceImpl(jobRepository, userRepository, projectRepository, eventBus);
    }

    @Test
    @DisplayName("submitJob - should create job and dispatch to worker when user exists")
    void submitJob_shouldCreateAndDispatch_whenUserExists(VertxTestContext testContext) {
        // Given
        long userId = 1L;
        Long projectId = null;
        JsonObject params = new JsonObject().put("task", "test");

        when(userRepository.existsById(userId)).thenReturn(Future.succeededFuture(true));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            return Future.succeededFuture(job);
        });

        // When
        jobService.submitJob(userId, projectId, params)
            .onComplete(testContext.succeeding(job -> testContext.verify(() -> {
                // Then
                assertThat(job).isNotNull();
                assertThat(job.getId()).isNotNull();
                assertThat(job.getUserId()).isEqualTo(userId);
                assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
                assertThat(job.getParameters()).isEqualTo(params);

                // Verify job was saved
                verify(jobRepository).save(any(Job.class));

                // Verify message was sent to event bus
                ArgumentCaptor<JsonObject> messageCaptor = ArgumentCaptor.forClass(JsonObject.class);
                verify(eventBus).send(eq(JobWorkerVerticle.JOB_PROCESS_ADDRESS), messageCaptor.capture());
                assertThat(messageCaptor.getValue().getString("jobId")).isEqualTo(job.getId());

                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("submitJob - should fail when user does not exist")
    void submitJob_shouldFail_whenUserNotFound(VertxTestContext testContext) {
        // Given
        long userId = 999L;
        JsonObject params = new JsonObject();

        when(userRepository.existsById(userId)).thenReturn(Future.succeededFuture(false));

        // When
        jobService.submitJob(userId, null, params)
            .onComplete(testContext.failing(err -> testContext.verify(() -> {
                // Then
                assertThat(err).isInstanceOf(IllegalArgumentException.class);
                assertThat(err.getMessage()).contains("User not found");
                verify(jobRepository, never()).save(any());
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("submitJob - should fail when project does not exist")
    void submitJob_shouldFail_whenProjectNotFound(VertxTestContext testContext) {
        // Given
        long userId = 1L;
        Long projectId = 999L;
        JsonObject params = new JsonObject();

        when(userRepository.existsById(userId)).thenReturn(Future.succeededFuture(true));
        when(projectRepository.existsById(projectId)).thenReturn(Future.succeededFuture(false));

        // When
        jobService.submitJob(userId, projectId, params)
            .onComplete(testContext.failing(err -> testContext.verify(() -> {
                // Then
                assertThat(err).isInstanceOf(IllegalArgumentException.class);
                assertThat(err.getMessage()).contains("Project not found");
                verify(jobRepository, never()).save(any());
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("submitJob - should succeed with valid project")
    void submitJob_shouldSucceed_withValidProject(VertxTestContext testContext) {
        // Given
        long userId = 1L;
        Long projectId = 1L;
        JsonObject params = new JsonObject();

        when(userRepository.existsById(userId)).thenReturn(Future.succeededFuture(true));
        when(projectRepository.existsById(projectId)).thenReturn(Future.succeededFuture(true));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> 
            Future.succeededFuture(invocation.getArgument(0)));

        // When
        jobService.submitJob(userId, projectId, params)
            .onComplete(testContext.succeeding(job -> testContext.verify(() -> {
                // Then
                assertThat(job.getProjectId()).isEqualTo(projectId);
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("getJob - should return job when found")
    void getJob_shouldReturnJob_whenFound(VertxTestContext testContext) {
        // Given
        String jobId = "test-job-id";
        Job expectedJob = Job.builder()
                .id(jobId)
                .userId(1L)
                .status(JobStatus.COMPLETED)
                .parameters(new JsonObject())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Future.succeededFuture(Optional.of(expectedJob)));

        // When
        jobService.getJob(jobId)
            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                // Then
                assertThat(result).isPresent();
                assertThat(result.get().getId()).isEqualTo(jobId);
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("getJob - should return empty when job not found")
    void getJob_shouldReturnEmpty_whenNotFound(VertxTestContext testContext) {
        // Given
        String jobId = "non-existent-job";

        when(jobRepository.findById(jobId)).thenReturn(Future.succeededFuture(Optional.empty()));

        // When
        jobService.getJob(jobId)
            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                // Then
                assertThat(result).isEmpty();
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("getJobsByUser - should return user's jobs")
    void getJobsByUser_shouldReturnUserJobs(VertxTestContext testContext) {
        // Given
        long userId = 1L;
        List<Job> expectedJobs = List.of(
                Job.builder().id("job1").userId(userId).status(JobStatus.PENDING)
                        .parameters(new JsonObject()).createdAt(Instant.now()).updatedAt(Instant.now()).build(),
                Job.builder().id("job2").userId(userId).status(JobStatus.COMPLETED)
                        .parameters(new JsonObject()).createdAt(Instant.now()).updatedAt(Instant.now()).build()
        );

        when(jobRepository.findByUserId(userId)).thenReturn(Future.succeededFuture(expectedJobs));

        // When
        jobService.getJobsByUser(userId)
            .onComplete(testContext.succeeding(jobs -> testContext.verify(() -> {
                // Then
                assertThat(jobs).hasSize(2);
                assertThat(jobs).extracting(Job::getUserId).containsOnly(userId);
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("getJobsByUser - should return empty list when no jobs found")
    void getJobsByUser_shouldReturnEmptyList_whenNoJobsFound(VertxTestContext testContext) {
        // Given
        long userId = 999L;

        when(jobRepository.findByUserId(userId)).thenReturn(Future.succeededFuture(List.of()));

        // When
        jobService.getJobsByUser(userId)
            .onComplete(testContext.succeeding(jobs -> testContext.verify(() -> {
                // Then
                assertThat(jobs).isEmpty();
                testContext.completeNow();
            })));
    }
}
