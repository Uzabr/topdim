ALTER TABLE merchants
    ADD COLUMN profile_version BIGINT NOT NULL DEFAULT 1;

CREATE TABLE merchant_profile_change_requests (
    id                   BIGSERIAL PRIMARY KEY,
    merchant_id          BIGINT       NOT NULL REFERENCES merchants(id),
    author_user_id       BIGINT       NOT NULL,
    author_staff_id      BIGINT,
    author_role          VARCHAR(16)  NOT NULL,
    base_profile_version BIGINT       NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    assignee_user_id     BIGINT,
    moderation_comment   VARCHAR(2000),
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    logo_url             VARCHAR(500),
    cover_url            VARCHAR(500),
    email                VARCHAR(255),
    website              VARCHAR(500),
    contact_person       VARCHAR(255),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at         TIMESTAMP,
    assigned_at          TIMESTAMP,
    decided_at           TIMESTAMP,
    withdrawn_at         TIMESTAMP,
    lock_version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_merchant_profile_change_author_role
        CHECK (author_role IN ('OWNER', 'MANAGER')),
    CONSTRAINT chk_merchant_profile_change_base_version
        CHECK (base_profile_version > 0),
    CONSTRAINT chk_merchant_profile_change_status
        CHECK (status IN (
            'DRAFT', 'PENDING_REVIEW', 'IN_REVIEW', 'REVISION_REQUESTED',
            'APPROVED', 'REJECTED', 'WITHDRAWN', 'OUTDATED'
        ))
);

CREATE TABLE merchant_profile_change_locations (
    id                 BIGSERIAL PRIMARY KEY,
    request_id         BIGINT       NOT NULL
        REFERENCES merchant_profile_change_requests(id) ON DELETE CASCADE,
    source_location_id BIGINT
        REFERENCES merchant_locations(id) ON DELETE SET NULL,
    title              VARCHAR(255),
    address            VARCHAR(500),
    phone              VARCHAR(50),
    working_hours      VARCHAR(255),
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    is_primary         BOOLEAN      NOT NULL DEFAULT FALSE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_merchant_profile_change_location_sort_order
        CHECK (sort_order >= 0)
);

CREATE TABLE merchant_profile_change_history (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT        NOT NULL
        REFERENCES merchant_profile_change_requests(id) ON DELETE CASCADE,
    previous_status VARCHAR(32),
    new_status      VARCHAR(32)   NOT NULL,
    actor_user_id   BIGINT        NOT NULL,
    actor_role      VARCHAR(32)   NOT NULL,
    comment         VARCHAR(2000),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_merchant_profile_change_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN (
            'DRAFT', 'PENDING_REVIEW', 'IN_REVIEW', 'REVISION_REQUESTED',
            'APPROVED', 'REJECTED', 'WITHDRAWN', 'OUTDATED'
        )),
    CONSTRAINT chk_merchant_profile_change_history_new_status
        CHECK (new_status IN (
            'DRAFT', 'PENDING_REVIEW', 'IN_REVIEW', 'REVISION_REQUESTED',
            'APPROVED', 'REJECTED', 'WITHDRAWN', 'OUTDATED'
        ))
);

CREATE INDEX idx_merchant_profile_change_merchant_status_updated
    ON merchant_profile_change_requests(merchant_id, status, updated_at DESC);

CREATE INDEX idx_merchant_profile_change_queue
    ON merchant_profile_change_requests(status, assignee_user_id, submitted_at);

CREATE INDEX idx_merchant_profile_change_merchant_base_version
    ON merchant_profile_change_requests(merchant_id, base_profile_version);

CREATE INDEX idx_merchant_profile_change_locations_request_sort
    ON merchant_profile_change_locations(request_id, sort_order);

CREATE INDEX idx_merchant_profile_change_history_request_created
    ON merchant_profile_change_history(request_id, created_at);
