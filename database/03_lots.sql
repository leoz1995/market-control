CREATE TABLE lots (

    id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    entry_date DATE NOT NULL,
    expiration_date DATE NOT NULL,
    total_received_quantity DECIMAL(15,3) NOT NULL,
    available_quantity DECIMAL(15,3) NOT NULL,

    CONSTRAINT pk_lots
        PRIMARY KEY (id),

    CONSTRAINT fk_lots_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_lots_total_received_positive
        CHECK (total_received_quantity > 0),

    CONSTRAINT chk_lots_available_nonnegative
        CHECK (available_quantity >= 0),

    CONSTRAINT chk_lots_available_not_greater_than_received
        CHECK (available_quantity <= total_received_quantity)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
