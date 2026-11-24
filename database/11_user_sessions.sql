CREATE TABLE user_sessions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    access_token_id CHAR(36) NOT NULL,
    refresh_token_hash CHAR(43) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_sessions_refresh_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_user_sessions_user (user_id),
    INDEX idx_user_sessions_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

GRANT SELECT, INSERT, UPDATE, DELETE ON marketcontrol.user_sessions TO 'marketcontrol_app'@'localhost';
GRANT SELECT, INSERT, UPDATE ON marketcontrol.users TO 'marketcontrol_app'@'localhost';
