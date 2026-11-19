-- Migration: give each lot one explicit, persistent reference to its original ENTRY item.
-- Run against the MarketControl database after 01..06 have been applied.
-- The preflight refuses ambiguous or incomplete history; it never deletes or chooses data.

DELIMITER //
DROP PROCEDURE IF EXISTS assert_lot_initial_entries_are_unambiguous//
CREATE PROCEDURE assert_lot_initial_entries_are_unambiguous()
BEGIN
    IF EXISTS (
        SELECT smi.lot_id
        FROM stock_movement_items smi
        JOIN stock_movements sm ON sm.id = smi.stock_movement_id
        WHERE sm.type = 1
        GROUP BY smi.lot_id
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration blocked: a lot has more than one ENTRY item; identify its original ENTRY before retrying.';
    END IF;

    IF EXISTS (
        SELECT l.id
        FROM lots l
        LEFT JOIN (
            SELECT smi.lot_id, COUNT(*) AS entry_count
            FROM stock_movement_items smi
            JOIN stock_movements sm ON sm.id = smi.stock_movement_id
            WHERE sm.type = 1
            GROUP BY smi.lot_id
        ) entries ON entries.lot_id = l.id
        WHERE COALESCE(entries.entry_count, 0) <> 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Migration blocked: every existing lot must have exactly one ENTRY item before migration.';
    END IF;
END//
DELIMITER ;

CALL assert_lot_initial_entries_are_unambiguous();
DROP PROCEDURE assert_lot_initial_entries_are_unambiguous;

ALTER TABLE stock_movement_items
    ADD COLUMN initial_entry_lot_id CHAR(36) NULL AFTER lot_id,
    ADD CONSTRAINT uq_movement_items_initial_entry_lot UNIQUE (initial_entry_lot_id),
    ADD CONSTRAINT chk_movement_item_initial_entry_lot
        CHECK (initial_entry_lot_id IS NULL OR initial_entry_lot_id = lot_id),
    ADD CONSTRAINT fk_movement_item_initial_entry_lot
        FOREIGN KEY (initial_entry_lot_id) REFERENCES lots(id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- The preflight established exactly one candidate per historical lot, so this backfill is deterministic.
UPDATE stock_movement_items smi
JOIN stock_movements sm ON sm.id = smi.stock_movement_id
SET smi.initial_entry_lot_id = smi.lot_id
WHERE sm.type = 1;

DELIMITER //
CREATE TRIGGER trg_movement_item_initial_entry_insert
BEFORE INSERT ON stock_movement_items
FOR EACH ROW
BEGIN
    DECLARE movement_type SMALLINT;

    SELECT type INTO movement_type
    FROM stock_movements
    WHERE id = NEW.stock_movement_id;

    IF NEW.initial_entry_lot_id IS NOT NULL THEN
        IF NEW.initial_entry_lot_id <> NEW.lot_id THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Initial ENTRY marker must reference the item lot.';
        END IF;

        IF movement_type <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Only an ENTRY movement can be marked as the lot initial entry.';
        END IF;
    END IF;
END//

CREATE TRIGGER trg_movement_item_initial_entry_update
BEFORE UPDATE ON stock_movement_items
FOR EACH ROW
BEGIN
    DECLARE movement_type SMALLINT;

    IF OLD.initial_entry_lot_id IS NOT NULL AND (
        NOT (NEW.initial_entry_lot_id <=> OLD.initial_entry_lot_id)
        OR NOT (NEW.lot_id <=> OLD.lot_id)
        OR NOT (NEW.stock_movement_id <=> OLD.stock_movement_id)
        OR NOT (NEW.unit_cost <=> OLD.unit_cost)
        OR NOT (NEW.quantity <=> OLD.quantity)
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The original ENTRY item and its historical cost are immutable.';
    END IF;

    IF NEW.initial_entry_lot_id IS NOT NULL THEN
        IF NEW.initial_entry_lot_id <> NEW.lot_id THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Initial ENTRY marker must reference the item lot.';
        END IF;

        SELECT type INTO movement_type
        FROM stock_movements
        WHERE id = NEW.stock_movement_id;

        IF movement_type <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Only an ENTRY movement can be marked as the lot initial entry.';
        END IF;
    END IF;
END//

CREATE TRIGGER trg_movement_item_initial_entry_delete
BEFORE DELETE ON stock_movement_items
FOR EACH ROW
BEGIN
    IF OLD.initial_entry_lot_id IS NOT NULL
       AND COALESCE(@marketcontrol_allow_origin_delete, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The original ENTRY item and its historical cost cannot be deleted.';
    END IF;
END//

CREATE TRIGGER trg_stock_movement_initial_entry_type_immutable
BEFORE UPDATE ON stock_movements
FOR EACH ROW
BEGIN
    IF OLD.type <> NEW.type AND EXISTS (
        SELECT 1 FROM stock_movement_items original_item
        WHERE original_item.stock_movement_id = OLD.id
          AND original_item.initial_entry_lot_id IS NOT NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The movement type of a lot original ENTRY cannot be changed.';
    END IF;
END//
DELIMITER ;
