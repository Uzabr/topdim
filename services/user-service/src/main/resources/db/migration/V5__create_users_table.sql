-- Create users table for user-service profile management
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    phone           VARCHAR(20) UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100),
    role            VARCHAR(50),
    avatar_url      VARCHAR(500),
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
