package com.example.jobserver.repositories.impl;

import com.example.jobserver.jooq.enums.JobsStatus;
import com.example.jobserver.model.Job;
import com.example.jobserver.model.JobResult;
import com.example.jobserver.model.JobStatus;
import com.example.jobserver.repositories.JobRepository;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.Query;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;

import static com.example.jobserver.jooq.Tables.JOBS_;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class JobRepositoryImpl implements JobRepository {

    private final Pool client;
    private final DSLContext dsl;

    public JobRepositoryImpl(Pool client) {
        this.client = client;
        this.dsl = DSL.using(org.jooq.SQLDialect.MYSQL);
    }

    @Override
    public Future<Job> save(Job job) {
        LocalDateTime now = LocalDateTime.now();
        
        // Use jOOQ to generate type-safe INSERT query
        Query query = dsl.insertInto(JOBS_)
                .columns(
                        JOBS_.ID,
                        JOBS_.USER_ID,
                        JOBS_.PROJECT_ID,
                        JOBS_.STATUS,
                        JOBS_.PARAMETERS,
                        JOBS_.CREATED_AT,
                        JOBS_.UPDATED_AT
                )
                .values(
                        job.getId(),
                        job.getUserId(),
                        job.getProjectId(),
                        toJooqStatus(job.getStatus()),
                        JSON.json(job.getParameters().encode()),
                        now,
                        now
                );

        String sql = query.getSQL(ParamType.INDEXED);
        Tuple params = Tuple.of(
                job.getId(),
                job.getUserId(),
                job.getProjectId(),
                job.getStatus().name(),
                job.getParameters().encode(),
                now,
                now
        );

        return client.preparedQuery(sql)
                .execute(params)
                .map(rows -> job).onFailure(err -> log.error("Failed to save job {}: {}", job.getId(), err.getMessage()));
    }

    @Override
    public Future<Optional<Job>> findById(String jobId) {
        // Use jOOQ to generate type-safe SELECT query
        Query query = dsl.select(
                        JOBS_.ID,
                        JOBS_.USER_ID,
                        JOBS_.PROJECT_ID,
                        JOBS_.STATUS,
                        JOBS_.PARAMETERS,
                        JOBS_.RESULT,
                        JOBS_.ERROR_MESSAGE,
                        JOBS_.CREATED_AT,
                        JOBS_.UPDATED_AT
                )
                .from(JOBS_)
                .where(JOBS_.ID.eq(jobId));

        String sql = query.getSQL(ParamType.INDEXED);

        return client.preparedQuery(sql)
                .execute(Tuple.of(jobId))
                .map(rows -> {
                    if (!rows.iterator().hasNext()) {
                        return Optional.empty();
                    }
                    Row row = rows.iterator().next();
                    return Optional.of(mapRow(row));
                });
    }

    @Override
    public Future<List<Job>> findByUserId(long userId) {
        // Use jOOQ to generate type-safe SELECT query
        Query query = dsl.select(
                        JOBS_.ID,
                        JOBS_.USER_ID,
                        JOBS_.PROJECT_ID,
                        JOBS_.STATUS,
                        JOBS_.PARAMETERS,
                        JOBS_.RESULT,
                        JOBS_.ERROR_MESSAGE,
                        JOBS_.CREATED_AT,
                        JOBS_.UPDATED_AT
                )
                .from(JOBS_)
                .where(JOBS_.USER_ID.eq(userId))
                .orderBy(JOBS_.CREATED_AT.desc());

        String sql = query.getSQL(ParamType.INDEXED);

        return client.preparedQuery(sql)
                .execute(Tuple.of(userId))
                .map(rows -> {
                    List<Job> jobs = new ArrayList<>();
                    for (Row row : rows) {
                        jobs.add(mapRow(row));
                    }
                    return jobs;
                });
    }

    @Override
    public Future<Void> updateStatus(String jobId, JobStatus status) {
        LocalDateTime now = LocalDateTime.now();
        
        // Use jOOQ to generate type-safe UPDATE query
        Query query = dsl.update(JOBS_)
                .set(JOBS_.STATUS, toJooqStatus(status))
                .set(JOBS_.UPDATED_AT, now)
                .where(JOBS_.ID.eq(jobId));

        String sql = query.getSQL(ParamType.INDEXED);
        Tuple params = Tuple.of(status.name(), now, jobId);

        return client.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    @Override
    public Future<Void> updateResult(String jobId, JobResult result) {
        LocalDateTime now = LocalDateTime.now();
        
        // Use jOOQ to generate type-safe UPDATE query
        Query query = dsl.update(JOBS_)
                .set(JOBS_.RESULT, JSON.json(result.payload().encode()))
                .set(JOBS_.UPDATED_AT, now)
                .where(JOBS_.ID.eq(jobId));

        String sql = query.getSQL(ParamType.INDEXED);
        Tuple params = Tuple.of(result.payload().encode(), now, jobId);

        return client.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    @Override
    public Future<Void> updateFailure(String jobId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        
        // Use jOOQ to generate type-safe UPDATE query
        Query query = dsl.update(JOBS_)
                .set(JOBS_.ERROR_MESSAGE, errorMessage)
                .set(JOBS_.UPDATED_AT, now)
                .where(JOBS_.ID.eq(jobId));

        String sql = query.getSQL(ParamType.INDEXED);
        Tuple params = Tuple.of(errorMessage, now, jobId);

        return client.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    private Job mapRow(Row row) {
        String id = row.getString("id");
        long userId = row.getLong("user_id");
        Long projectId = row.getLong("project_id");
        if (row.getValue("project_id") == null) {
            projectId = null;
        }
        JobStatus status = JobStatus.valueOf(row.getString("status"));
        JsonObject params = row.getJsonObject("parameters");
    
        JsonObject resultJson = row.getJsonObject("result");
        String error = row.getString("error_message");
        Instant createdAt = row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC);
        Instant updatedAt = row.getLocalDateTime("updated_at").toInstant(ZoneOffset.UTC);

        Job job = new Job(id, userId, projectId, status, params, null, error, createdAt, updatedAt);
        if (resultJson != null) {
            job.setResult(new JobResult(resultJson));
        }
        return job;
    }

    private JobsStatus toJooqStatus(JobStatus status) {
        if(status == null) {
            return null;
        }
        return JobsStatus.valueOf(status.name());
    }
}
