-- V21: Add optimistic lock version column to coupon_options for atomic stock reservation.
-- Prevents oversell race condition (CRITICAL-1 from PRD audit).
ALTER TABLE coupon_options ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
