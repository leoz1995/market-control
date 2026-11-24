CREATE TABLE users (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password VARCHAR(60) NOT NULL,
    update_time DATETIME NULL,
    entry_time DATETIME NOT NULL,
    role SMALLINT NOT NULL,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_name_not_blank
        CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_users_email_not_blank
        CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    CONSTRAINT chk_users_role
        CHECK (role IN (1, 2, 3))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
