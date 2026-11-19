-- Migration 08: close the direct-write gap left by migration 07.
-- Run as a database administrator after 01..07. This deployment grants the
-- application account used by database.properties: marketcontrol_app@localhost.

DELIMITER //
DROP PROCEDURE IF EXISTS assert_lot_origins_are_complete//
CREATE PROCEDURE assert_lot_origins_are_complete()
BEGIN
    IF EXISTS (
        SELECT l.id
        FROM lots l
        LEFT JOIN stock_movement_items original_item
            ON original_item.initial_entry_lot_id = l.id
        WHERE original_item.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration blocked: every lot must already have one marked original ENTRY.';
    END IF;

    IF EXISTS (
        SELECT original_item.id
        FROM stock_movement_items original_item
        JOIN stock_movements sm ON sm.id = original_item.stock_movement_id
        WHERE original_item.initial_entry_lot_id IS NOT NULL
          AND sm.type <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration blocked: a marked original item is not an ENTRY movement.';
    END IF;
END//
DELIMITER ;

CALL assert_lot_origins_are_complete();
DROP PROCEDURE assert_lot_origins_are_complete;

DELIMITER //
DROP TRIGGER IF EXISTS trg_movement_item_initial_entry_delete//
CREATE TRIGGER trg_movement_item_initial_entry_delete
BEFORE DELETE ON stock_movement_items
FOR EACH ROW
BEGIN
    IF OLD.initial_entry_lot_id IS NOT NULL
       AND NOT (
           COALESCE(@marketcontrol_allow_origin_delete, 0) = 1
           AND SUBSTRING_INDEX(USER(), '@', 1) = 'root'
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The original ENTRY item and its historical cost cannot be deleted.';
    END IF;
END//

DROP PROCEDURE IF EXISTS create_lot_with_initial_entry//
CREATE PROCEDURE create_lot_with_initial_entry(
    IN p_lot_id CHAR(36),
    IN p_product_id CHAR(36),
    IN p_entry_date DATE,
    IN p_expiration_date DATE,
    IN p_initial_quantity DECIMAL(15,3),
    IN p_supplier_id CHAR(36),
    IN p_unit_cost DECIMAL(15,2)
)
SQL SECURITY DEFINER
BEGIN
    DECLARE v_movement_id CHAR(36);
    DECLARE v_owns_transaction BOOLEAN DEFAULT FALSE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        IF v_owns_transaction THEN
            ROLLBACK;
        END IF;
        RESIGNAL;
    END;

    IF p_initial_quantity IS NULL OR p_initial_quantity <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Initial lot quantity must be positive.';
    END IF;
    IF p_unit_cost IS NULL OR p_unit_cost <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Initial lot unit cost must be positive.';
    END IF;
    IF p_entry_date IS NULL OR p_expiration_date IS NULL OR p_expiration_date < p_entry_date THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lot dates are invalid.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM suppliers WHERE id = p_supplier_id AND active = TRUE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'An active supplier is required for the initial ENTRY.';
    END IF;

    IF @@session.autocommit = 1 THEN
        START TRANSACTION;
        SET v_owns_transaction = TRUE;
    END IF;

    SET v_movement_id = UUID();

    INSERT INTO lots (id, product_id, entry_date, expiration_date, available_quantity)
    VALUES (p_lot_id, p_product_id, p_entry_date, p_expiration_date, p_initial_quantity);

    INSERT INTO stock_movements (id, type, date_time, supplier_id, description)
    VALUES (v_movement_id, 1, CURRENT_TIMESTAMP, p_supplier_id, 'Entrada inicial do lote.');

    INSERT INTO stock_movement_items
        (id, stock_movement_id, lot_id, quantity, unit_cost, initial_entry_lot_id)
    VALUES
        (UUID(), v_movement_id, p_lot_id, p_initial_quantity, p_unit_cost, p_lot_id);

    IF v_owns_transaction THEN
        COMMIT;
    END IF;
END//
DELIMITER ;

-- Remove broad schema-level INSERT, then restore only the tables that the
-- application is allowed to create directly. New lots must use the procedure.
REVOKE INSERT ON marketcontrol.* FROM 'marketcontrol_app'@'localhost';
GRANT INSERT ON marketcontrol.categories TO 'marketcontrol_app'@'localhost';
GRANT INSERT ON marketcontrol.products TO 'marketcontrol_app'@'localhost';
GRANT INSERT ON marketcontrol.suppliers TO 'marketcontrol_app'@'localhost';
GRANT INSERT ON marketcontrol.stock_movements TO 'marketcontrol_app'@'localhost';
GRANT INSERT ON marketcontrol.stock_movement_items TO 'marketcontrol_app'@'localhost';
GRANT EXECUTE ON PROCEDURE marketcontrol.create_lot_with_initial_entry TO 'marketcontrol_app'@'localhost';
