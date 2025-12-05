package com.example.jobserver.repositories;

import io.vertx.core.Future;

public interface UserRepository {
    Future<Boolean> existsById(long userId);
}
