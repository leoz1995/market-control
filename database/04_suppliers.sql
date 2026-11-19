CREATE TABLE suppliers (
    id CHAR(36) NOT NULL PRIMARY KEY,
    legal_name VARCHAR(150) NOT NULL,
    trade_name VARCHAR(150) NOT NULL,
    document CHAR(14) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,

    CONSTRAINT uk_suppliers_document UNIQUE (document)
) ENGINE = InnoDB;