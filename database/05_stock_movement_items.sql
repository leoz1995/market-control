CREATE TABLE stock_movement_items (
	id CHAR(36) PRIMARY KEY NOT NULL,
	stock_movement_id CHAR(36) NOT NULL,
	lot_id CHAR(36) NOT NULL,
	quantity decimal(15,3) NOT NULL,
	
    CONSTRAINT fk_movement_item_movement
        FOREIGN KEY (stock_movement_id)
        REFERENCES stock_movements(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_movement_item_lot
        FOREIGN KEY (lot_id)
        REFERENCES lots(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_movement_item_quantity
        CHECK (quantity > 0)
) ENGINE = InnoDB;

CREATE INDEX idx_movement_items_movement
    ON stock_movement_items(stock_movement_id);

CREATE INDEX idx_movement_items_lot
    ON stock_movement_items(lot_id);