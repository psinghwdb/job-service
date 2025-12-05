package com.example.jobserver.clients.impl;

import com.example.jobserver.clients.Impl.ThirdPartyPythonClient;
import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobStatus;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ThirdPartyPythonClientTest {

    private static final String EXTERNAL_API_URL = "http://localhost:8081/";

    @Mock
    private WebClient webClient;

    @Mock
    private HttpRequest<Buffer> httpRequest;

    @Mock
    private HttpResponse<Buffer> httpResponse;

    private ThirdPartyPythonClient client;

    @BeforeEach
    void setUp() {
        client = new ThirdPartyPythonClient(webClient, EXTERNAL_API_URL);
    }

    @Test
    @DisplayName("process - should return JobResult on successful API call")
    void process_shouldReturnJobResult_onSuccess(VertxTestContext testContext) {
        // Given
        Job job = Job.builder()
                .id("test-job-id")
                .userId(1L)
                .status(JobStatus.PROCESSING)
                .parameters(new JsonObject().put("task", "test"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        JsonObject responseBody = new JsonObject()
                .put("status", "success")
                .put("data", new JsonObject().put("result", "processed"));

        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(httpResponse));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.bodyAsJsonObject()).thenReturn(responseBody);

        // When
        client.process(job)
            .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                // Then
                assertThat(result).isNotNull();
                assertThat(result.payload()).isEqualTo(responseBody);
                
                // Verify API was called with correct URL
                verify(webClient).postAbs("http://localhost:8081/process");
                verify(httpRequest).sendJsonObject(argThat(body -> 
                    body.getString("jobId").equals("test-job-id")
                ));
                
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("process - should fail when API returns error status")
    void process_shouldFail_whenApiReturnsErrorStatus(VertxTestContext testContext) {
        // Given
        Job job = Job.builder()
                .id("test-job-id")
                .userId(1L)
                .status(JobStatus.PROCESSING)
                .parameters(new JsonObject())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.sendJsonObject(any(JsonObject.class))).thenReturn(Future.succeededFuture(httpResponse));
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.bodyAsString()).thenReturn("Internal Server Error");

        // When
        client.process(job)
            .onComplete(testContext.failing(err -> testContext.verify(() -> {
                // Then
                assertThat(err.getMessage()).contains("External API returned error: 500");
                testContext.completeNow();
            })));
    }

    @Test
    @DisplayName("process - should fail when connection error")
    void process_shouldFail_whenConnectionError(VertxTestContext testContext) {
        // Given
        Job job = Job.builder()
                .id("test-job-id")
                .userId(1L)
                .status(JobStatus.PROCESSING)
                .parameters(new JsonObject())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(webClient.postAbs(anyString())).thenReturn(httpRequest);
        when(httpRequest.sendJsonObject(any(JsonObject.class)))
                .thenReturn(Future.failedFuture(new RuntimeException("Connection refused")));

        // When
        client.process(job)
            .onComplete(testContext.failing(err -> testContext.verify(() -> {
                // Then
                assertThat(err.getMessage()).contains("Connection refused");
                testContext.completeNow();
            })));
    }
}
