# Async Job Server

A high-performance asynchronous job processing server built with **Vert.x**, **jOOQ**, and **MySQL**.

## 🌐 Live URLs

| URL | Description |
|-----|-------------|
| **http://localhost:8067/** | Web UI - View users, job list, and job details |
| **http://localhost:8067/docs** | Swagger UI - Interactive API documentation |
| **http://localhost:8067/api/openapi.yaml** | OpenAPI 3.0 Specification |

## 📱 Web UI Features

The web UI at `http://localhost:8067/` provides:
- **User Selection** - Dropdown to select a user
- **Job List** - View all jobs for the selected user with status badges
- **Job Submission** - Form to submit new jobs with parameters
- **Job Details** - Click any job to view full details including result/error

## 🔌 API Endpoints

Three REST endpoints defined in `openapi.yaml`:

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/jobs` | **Create Job** - Submit a new job for processing |
| `GET` | `/jobs/user/{userId}` | **Get Jobs by User** - List all jobs for a user |
| `GET` | `/jobs/{jobId}` | **Get Job Detail** - Get full details of a specific job |

### Example: Create a Job

```bash
curl -X POST http://localhost:8067/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "projectId": 1,
    "parameters": { "task": "demo" }
  }'
```

Response:
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING"
}
```

## ⚙️ Job Processing Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     HTTP Request (POST /jobs)                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        JobService                                │
│   • Validates user/project exists                               │
│   • Saves job to DB with PENDING status                         │
│   • Sends job ID to Event Bus (fire-and-forget)                 │
│   • Returns 202 Accepted immediately                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Vert.x Event Bus                              │
│              (Async message passing)                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│               Worker Verticles (4 instances)                     │
│                                                                  │
│   Thread 1 ──┐                                                   │
│   Thread 2 ──┼── Process jobs in parallel                       │
│   Thread 3 ──┤   • Non-blocking to each other                   │
│   Thread 4 ──┘   • Call external Python API                     │
│                  • Update job status in DB                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              External Python Service (port 8081)                 │
│                   POST /process                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Job Status Flow

```
PENDING → PROCESSING → COMPLETED
                    ↘ FAILED
```

### Worker Thread Pool

- **4 worker threads** process jobs concurrently
- Workers are **non-blocking** - they don't wait for each other
- Uses **fire-and-forget** pattern - API returns immediately
- Job status tracked via database updates

## 🗄️ Database

### Flyway Migrations

- **Flyway** manages database schema migrations
- Migration files located in `src/main/resources/db/migration/`
- **On Docker**: Flyway runs automatically on application start
- **Local development**: Run manually with `mvn flyway:migrate`

### jOOQ Code Generation

- **jOOQ** provides type-safe SQL queries
- Generated classes in `src/main/java/com/example/jobserver/jooq/`
- **Generation order**: Flyway first → then jOOQ codegen
- **Development**: Run `mvn jooq-codegen:generate` after migrations
- **Docker**: Classes pre-generated, Flyway runs on start

```bash
# Local development workflow
mvn flyway:migrate           # Run migrations first
mvn jooq-codegen:generate    # Generate jOOQ classes
mvn compile                  # Compile with generated classes
```

## 🏗️ Architecture & Design Principles

### MVP (Model-View-Presenter) Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Web Layer                                │
│   OpenApiJobRouter.java - HTTP handlers from OpenAPI spec       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Service Layer                               │
│   JobService (interface) → JobServiceImpl (implementation)      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Repository Layer                              │
│   JobRepository (interface) → JobRepositoryImpl (jOOQ queries)  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Database (MySQL)                            │
└─────────────────────────────────────────────────────────────────┘
```

### SOLID Principles Applied

| Principle | Implementation |
|-----------|----------------|
| **S** - Single Responsibility | Each class has one job: `JobService` handles business logic, `JobRepository` handles data access, `JobWorkerVerticle` handles async processing |
| **O** - Open/Closed | New job processors can be added by implementing `ExternalJobProcessor` interface without modifying existing code |
| **L** - Liskov Substitution | Implementations can be swapped: `JobRepositoryImpl` can be replaced with `MockJobRepository` for testing |
| **I** - Interface Segregation | Small, focused interfaces: `JobRepository`, `UserRepository`, `ProjectRepository` are separate |
| **D** - Dependency Injection | All dependencies injected via constructors, making testing easy with mocks |

### Key Interfaces

```java
// Service layer interface - implementation can change
public interface JobService {
    Future<Job> submitJob(long userId, Long projectId, JsonObject params);
    Future<Optional<Job>> getJob(String jobId);
    Future<List<Job>> getJobsByUser(long userId);
}

// Repository layer interface - can swap DB implementations
public interface JobRepository {
    Future<Job> save(Job job);
    Future<Optional<Job>> findById(String jobId);
    Future<List<Job>> findByUserId(long userId);
    Future<Void> updateStatus(String jobId, JobStatus status);
}

// External processor interface - can swap to different services
public interface ExternalJobProcessor {
    Future<JobResult> process(Job job);
}
```

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- MySQL database (port 3307)
- Mock Python external service (port 8081)

### 2. Run Migrations & Generate Code

```bash
cd job-server
mvn flyway:migrate
mvn jooq-codegen:generate
```

### 3. Start the Application

```bash
mvn compile exec:java \
  -Dexec.mainClass="io.vertx.core.Launcher" \
  -Dexec.args="run com.example.jobserver.MainVerticle"
```

### 4. Access the Application

- **Web UI**: http://localhost:8067/
- **Swagger UI**: http://localhost:8067/docs
- **API Spec**: http://localhost:8067/api/openapi.yaml

## 🧪 Running Tests

```bash
mvn test
```

Tests include:
- `JobServiceImplTest` - Service layer unit tests
- `JobRepositoryImplTest` - Repository layer tests
- `ThirdPartyPythonClientTest` - External client tests

## 📁 Project Structure

```
job-server/
├── src/main/java/com/example/jobserver/
│   ├── MainVerticle.java              # Application entry point
│   ├── clients/                       # External service clients
│   │   ├── ExternalJobProcessor.java  # Interface
│   │   └── Impl/
│   │       └── ThirdPartyPythonClient.java
│   ├── model/                         # Domain models
│   │   ├── Job.java
│   │   ├── JobResult.java
│   │   └── JobStatus.java
│   ├── repositories/                  # Data access interfaces
│   │   ├── JobRepository.java
│   │   └── impl/
│   │       └── JobRepositoryImpl.java
│   ├── service/                       # Business logic
│   │   ├── JobService.java            # Interface
│   │   └── impl/
│   │       └── JobServiceImpl.java
│   ├── web/                           # HTTP layer
│   │   └── OpenApiJobRouter.java      # OpenAPI-generated routes
│   ├── worker/                        # Background processing
│   │   └── JobWorkerVerticle.java
│   └── jooq/                          # Generated jOOQ classes
├── src/main/resources/
│   ├── db/migration/                  # Flyway SQL migrations
│   ├── webroot/                       # Static UI files
│   │   ├── index.html                 # Main UI
│   │   └── swagger-ui.html            # API docs
│   └── openapi.yaml                   # OpenAPI 3.0 specification
├── src/test/java/                     # Unit tests
└── pom.xml                            # Maven configuration
```

## 🔧 Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3307` | MySQL port |
| `DB_USER` | `root` | MySQL username |
| `DB_PASS` | `root` | MySQL password |
| `DB_NAME` | `jobs` | Database name |

## 🛠️ Technologies

| Technology | Purpose |
|------------|---------|
| **Vert.x 4.5.x** | Reactive, non-blocking toolkit |
| **jOOQ 3.19.x** | Type-safe SQL query builder |
| **Flyway 10.x** | Database migrations |
| **MySQL 8.x** | Relational database |
| **OpenAPI 3.0** | API specification & documentation |
| **Swagger UI** | Interactive API documentation |
| **Lombok** | Reduce boilerplate code |
| **JUnit 5 + Mockito** | Unit testing |
