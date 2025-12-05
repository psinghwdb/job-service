package com.example.jobserver.repositories.impl;

import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobResult;
import com.example.jobserver.model.JobStatus;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class JobRepositoryImplTest {

    @Mock
    private Pool pool;

    @Mock
    private PreparedQuery<RowSet<Row>> preparedQuery;

    @Mock
    private RowSet<Row> rowSet;

    @Mock
    private Row row;

    private JobRepositoryImpl jobRepository;

    @BeforeEach
    void setUp() {
        jobRepository = new JobRepositoryImpl(pool);
    }

    @Test
    @DisplayName("save - should insert job and return it")
    void save_shouldInsertJob(VertxTestContext testContext) {
        // Given
        Job job = Job.builder()
                .id("test-id")
                .userId(1L)
                .projectId(1L)
                .status(JobStatus.PENDING)
                .parameters(new JsonObject().put("task", "test"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pool.preparedQuery(anyString())).thenReturn(preparedQuery);
        when(preparedQuery.execute(any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));

        // When
        jobRepository.save(job)
            .onComplete(testContext.succeeding(savedJob -> testContext.verify(() -> {
                // Then
                assertThat(savedJob).isEqualTo(job);
                verify(pool).preparedQuery(anyString());
                verify(preparedQuery).execute(any(Tuple.class));
                testContext.completeNow();
            })));
    }

    
   

}

