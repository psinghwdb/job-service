package com.example.jobserver.config;

import com.example.jobserver.clients.ExternalJobProcessor;
import com.example.jobserver.clients.Impl.ThirdPartyPythonClient;
import com.example.jobserver.repositories.JobRepository;
import com.example.jobserver.repositories.ProjectRepository;
import com.example.jobserver.repositories.UserRepository;
import com.example.jobserver.repositories.impl.JobRepositoryImpl;
import com.example.jobserver.service.JobService;
import com.example.jobserver.service.impl.JobServiceImpl;
import com.example.jobserver.worker.JobWorkerVerticle;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.client.WebClient;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.flywaydb.core.Flyway;

/**
 * Dependency injection module - creates and wires all application components.
 * Single Responsibility: Only handles object creation and wiring.
 */
@Slf4j
@Getter
public class AppModule {

    private final AppConfig config;
    private final Pool dbPool;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ExternalJobProcessor externalProcessor;
    private final JobService jobService;
    private final JobWorkerVerticle jobWorkerVerticle;

    public AppModule(Vertx vertx, AppConfig config) {
        this.config = config;
        
        // 1. Database pool
        this.dbPool = createDatabasePool(vertx, config);
        
        // 2. Run migrations
        runMigrations(config);
        
        // 3. Repositories
        this.jobRepository = new JobRepositoryImpl(dbPool);
        this.userRepository = createUserRepository(dbPool);
        this.projectRepository = createProjectRepository(dbPool);
        
        // 4. External processor
        WebClient webClient = WebClient.create(vertx);
        this.externalProcessor = new ThirdPartyPythonClient(webClient,config.getExternalApiUrl());
        
        // 5. Worker verticle
        this.jobWorkerVerticle = new JobWorkerVerticle(jobRepository, externalProcessor);
        
        // 6. Services
        EventBus eventBus = vertx.eventBus();
        this.jobService = new JobServiceImpl(jobRepository, userRepository, projectRepository, eventBus);
        
        log.info("AppModule initialized successfully");
    }

    private Pool createDatabasePool(Vertx vertx, AppConfig config) {
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
            .setPort(config.getDbPort())
            .setHost(config.getDbHost())
            .setDatabase(config.getDbName())
            .setUser(config.getDbUser())
            .setPassword(config.getDbPassword())
            .setSsl(false);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(config.getDbPoolSize());
        
        return MySQLPool.pool(vertx, connectOptions, poolOptions);
    }

    private void runMigrations(AppConfig config) {
        log.info("Running database migrations...");
        Flyway flyway = Flyway.configure()
            .dataSource(config.getJdbcUrl(), config.getDbUser(), config.getDbPassword())
            .load();
        flyway.migrate();
        log.info("Database migrations completed");
    }

    
    private UserRepository createUserRepository(Pool dbPool) {
      
        return userId -> Future.succeededFuture(true);
    }

  
    private ProjectRepository createProjectRepository(Pool dbPool) {
      
        return projectId -> Future.succeededFuture(true);
    }

    /**
     * Factory method to create a new JobWorkerVerticle instance.
     * Used for deploying multiple worker instances.
     */
    public JobWorkerVerticle createWorkerVerticle() {
        return new JobWorkerVerticle(jobRepository, externalProcessor);
    }
}

