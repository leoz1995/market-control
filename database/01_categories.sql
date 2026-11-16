CREATE TABLE categories (
	id CHAR(36) PRIMARY KEY UNIQUE,
	name VARCHAR(100) NOT NULL,
	description TEXT NOT NULL,
	active BOOLEAN NOT NULL,
	CONSTRAINT chk_categories_description_not_blank
		CHECK (CHAR_LENGTH(TRIM(description)) > 0)
);
