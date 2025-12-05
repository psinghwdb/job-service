package com.example.jobserver.repositories;

import io.vertx.core.Future;

public interface ProjectRepository {
    Future<Boolean> existsById(long projectId);
}
