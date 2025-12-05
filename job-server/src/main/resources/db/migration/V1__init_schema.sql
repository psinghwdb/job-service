CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE jobs (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL,
    parameters JSON NOT NULL,
    result JSON NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (project_id) REFERENCES projects(id)
);

INSERT INTO users (username, email)
VALUES ('parminder', 'ghuman1986@gmail.com'),
       ('test', 'test@example.com');

INSERT INTO projects (name)
VALUES ('Project 1'),
       ('Project 2');
