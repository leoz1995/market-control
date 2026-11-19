CREATE TABLE stock_movements (
    id CHAR(36) NOT NULL PRIMARY KEY,
    type SMALLINT NOT NULL,
    date_time DATETIME NOT NULL,
    supplier_id CHAR(36) NULL,
    description VARCHAR(255) NOT NULL,

    INDEX idx_stock_movements_supplier_id (supplier_id),

    CONSTRAINT fk_stock_movements_supplier
        FOREIGN KEY (supplier_id)
        REFERENCES suppliers(id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) ENGINE = InnoDB;