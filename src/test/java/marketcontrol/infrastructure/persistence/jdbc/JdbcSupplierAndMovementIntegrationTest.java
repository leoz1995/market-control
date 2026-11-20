package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.StockMovementItem;
import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.http.server.infrastructure.database.DatabaseConnection;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcLotRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcStockMovementRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcSupplierRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcTransactionManager;
import marketcontrol.service.SupplierService;
import marketcontrol.service.StockMovementService;

/** Real MariaDB tests with explicitly seeded supplier, product and lot fixtures. */
class JdbcSupplierAndMovementIntegrationTest {
    private final String run = UUID.randomUUID().toString();
    private UUID categoryId, productId, lotId, supplierId;
    private UUID fixtureEntryId;
    private UUID extraSupplierId;
    private Supplier supplier;
    private final JdbcSupplierRepository suppliers = new JdbcSupplierRepository();
    private final JdbcStockMovementRepository movements = new JdbcStockMovementRepository();
    private final JdbcTransactionManager tx = new JdbcTransactionManager();

    @BeforeEach void setUp() throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); var st = c.createStatement(); var rs = st.executeQuery("SELECT DATABASE()")) {
            assertTrue(rs.next());
            assertEquals("marketcontrol", rs.getString(1), "Integration tests may only use the authorized test database");
        }
        categoryId = UUID.randomUUID(); productId = UUID.randomUUID(); lotId = UUID.randomUUID(); supplierId = UUID.randomUUID();
        supplier = Supplier.reconstitute(supplierId, "Test legal", "Test trade", "12345678901234", "test@market.invalid", "555", "Test", true, LocalDateTime.now());
        exec("INSERT INTO categories (id,name,description,active) VALUES (?,?,?,?)", categoryId.toString(), "IT "+run, "Integration fixture", true);
        exec("INSERT INTO products (id,name,bar_code,description,category_id,unit) VALUES (?,?,?,?,?,?)", productId.toString(), "IT product", "IT-"+run, null, categoryId.toString(), UnitOfMeasure.UNIT.getCode());
        exec("INSERT INTO suppliers (id,legal_name,trade_name,document,email,phone,contact_name,active,created_at) VALUES (?,?,?,?,?,?,?,?,?)", supplierId.toString(), supplier.getLegalName(), supplier.getTradeName(), supplier.getDocument(), supplier.getEmail(), supplier.getPhone(), supplier.getContactName(), true, java.sql.Timestamp.valueOf(supplier.getCreatedAt()));
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("CALL create_lot_with_initial_entry(?,?,?,?,?,?,?)")) {
            p.setString(1, lotId.toString());
            p.setString(2, productId.toString());
            p.setDate(3, java.sql.Date.valueOf(LocalDate.now().minusDays(1)));
            p.setDate(4, java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
            p.setBigDecimal(5, new BigDecimal("20"));
            p.setString(6, supplierId.toString());
            p.setBigDecimal(7, new BigDecimal("3.25"));
            p.execute();
        }
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT stock_movement_id FROM stock_movement_items WHERE initial_entry_lot_id=?")) {
            p.setString(1, lotId.toString());
            try (ResultSet rs = p.executeQuery()) { assertTrue(rs.next()); fixtureEntryId = UUID.fromString(rs.getString(1)); }
        }
    }

    @AfterEach void cleanUp() throws Exception {
        try (Connection c = DatabaseConnection.getAdminConnection()) {
            // The root-only session override is confined to disposable test fixtures.
            try (var st = c.createStatement()) { st.execute("SET @marketcontrol_allow_origin_delete = 1"); }
            if (lotId != null) {
                List<String> movementIds = new java.util.ArrayList<>();
                try (PreparedStatement p = c.prepareStatement("SELECT DISTINCT stock_movement_id FROM stock_movement_items WHERE lot_id=?")) {
                    p.setString(1, lotId.toString()); try (ResultSet rs = p.executeQuery()) { while (rs.next()) movementIds.add(rs.getString(1)); }
                }
                delete(c, "DELETE FROM stock_movement_items WHERE lot_id = ?", lotId.toString());
                for (String movementId : movementIds) delete(c, "DELETE FROM stock_movements WHERE id = ?", movementId);
                delete(c, "DELETE FROM lots WHERE id = ?", lotId.toString());
            }
            if (supplierId != null) delete(c, "DELETE FROM suppliers WHERE id = ?", supplierId.toString());
            if (extraSupplierId != null) delete(c, "DELETE FROM suppliers WHERE id = ?", extraSupplierId.toString());
            if (productId != null) delete(c, "DELETE FROM products WHERE id = ?", productId.toString());
            if (categoryId != null) delete(c, "DELETE FROM categories WHERE id = ?", categoryId.toString());
        }
    }

    private static void delete(Connection c, String sql, String value) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) { p.setString(1, value); p.executeUpdate(); }
    }

    @Test void supplierInsertLookupAndUniqueDocumentConstraint() {
        Supplier extra = Supplier.create("Second legal", "Second trade", "98765432109876", "second@market.invalid", "555", "Person");
        extraSupplierId = extra.getId();
        suppliers.save(extra);
        assertEquals(extra.getId(), suppliers.findById(extra.getId()).orElseThrow().getId());
        assertTrue(suppliers.existsByDocument(extra.getDocument()));
        Supplier duplicate = Supplier.create("Duplicate", "Duplicate", extra.getDocument(), "dup@market.invalid", "555", "Person");
        assertThrows(PersistenceException.class, () -> suppliers.save(duplicate));
    }

    @Test void supplierDetailsUpdateIsPersistedAndKeepsIdentityAndCreationDate() {
        Supplier original = suppliers.findById(supplierId).orElseThrow();

        var updated = new SupplierService(suppliers).update(supplierId, "Updated Legal", "Updated Trade", "98765432109876",
                "updated@market.invalid", "555-0200", "Updated Contact");
        Supplier persisted = suppliers.findById(supplierId).orElseThrow();

        assertAll(
                () -> assertEquals(supplierId, updated.getId()),
                () -> assertEquals(original.getCreatedAt(), updated.getCreatedAt()),
                () -> assertEquals("updated legal", persisted.getLegalName()),
                () -> assertEquals("updated trade", persisted.getTradeName()),
                () -> assertEquals("98765432109876", persisted.getDocument()),
                () -> assertEquals("updated@market.invalid", persisted.getEmail()),
                () -> assertEquals("555-0200", persisted.getPhone()),
                () -> assertEquals("updated contact", persisted.getContactName()),
                () -> assertTrue(persisted.isActive()));
    }

    @Test void deactivationPersistsAndKeepsHistoricalSupplierAndMovement() throws Exception {
        SupplierService service = new SupplierService(suppliers);
        var inactive = service.deactivate(supplierId);
        Supplier persisted = suppliers.findById(supplierId).orElseThrow();

        assertFalse(inactive.isActive());
        assertFalse(persisted.isActive());
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movements WHERE id=? AND supplier_id=?", fixtureEntryId.toString(), supplierId.toString()));

        StockMovement historical = StockMovement.reconstitute(fixtureEntryId, MovementType.ENTRY, LocalDateTime.now(),
                "Historical fixture", persisted, List.of(StockMovementItem.create(lotId, new BigDecimal("20"), new BigDecimal("3.25"))));
        assertFalse(historical.getSupplier().isActive());
    }

    @Test void supplierWithHistoryCannotBePhysicallyDeleted() throws Exception {
        assertThrows(SQLException.class, () -> exec("DELETE FROM suppliers WHERE id=?", supplierId.toString()));

        assertEquals(1, count("SELECT COUNT(*) FROM suppliers WHERE id=?", supplierId.toString()));
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movements WHERE id=? AND supplier_id=?", fixtureEntryId.toString(), supplierId.toString()));
    }

    @Test void updateAndDeactivationRejectMissingSupplier() {
        SupplierService service = new SupplierService(suppliers);
        UUID missingId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.update(missingId, "Legal", "Trade", "12345678901234", "a@b", "555", "Contact"));
        assertThrows(IllegalArgumentException.class, () -> service.deactivate(missingId));

        Supplier missing = Supplier.reconstitute(missingId, "Legal", "Trade", "12345678901234", "a@b", "555", "Contact", true, LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> suppliers.update(missing));
    }

    @Test void failedUpdateDueToUniqueDocumentConstraintLeavesSupplierUnchanged() {
        Supplier duplicate = Supplier.create("Duplicate legal", "Duplicate trade", "98765432109876", "duplicate@market.invalid", "555", "Duplicate");
        extraSupplierId = duplicate.getId();
        suppliers.save(duplicate);

        Supplier conflictingUpdate = Supplier.reconstitute(supplierId, "Changed legal", "Changed trade", duplicate.getDocument(),
                "changed@market.invalid", "555-9999", "Changed", false, supplier.getCreatedAt());
        assertThrows(PersistenceException.class, () -> suppliers.update(conflictingUpdate));

        Supplier persisted = suppliers.findById(supplierId).orElseThrow();
        assertEquals("Test legal", persisted.getLegalName());
        assertEquals("12345678901234", persisted.getDocument());
        assertTrue(persisted.isActive());
    }

    @Test void nullableSupplierAndMovementItemsPersistAndHistoricalEntryCostIsRecovered() throws Exception {
        UUID exitId = UUID.randomUUID();
        StockMovement exit = StockMovement.reconstitute(exitId, MovementType.EXIT, LocalDateTime.now(), "nullable supplier", null,
                List.of(StockMovementItem.create(lotId, new BigDecimal("1.5"), new BigDecimal("3.25"))));
        tx.execute(conn -> movements.save(conn, exit));
        assertEquals(new BigDecimal("3.25"), tx.executeAndReturn(conn -> movements.findInitialEntryByLotId(conn, lotId).orElseThrow()));
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT supplier_id FROM stock_movements WHERE id=?")) {
            p.setString(1, exitId.toString()); try (ResultSet rs = p.executeQuery()) { assertTrue(rs.next()); assertNull(rs.getString(1)); }
        }
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement_items WHERE stock_movement_id=?", exitId.toString()));
    }

    @Test void originalEntryIsPersistedAndDuplicateOriginalIsRejectedByMariaDb() throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement_items WHERE initial_entry_lot_id=?", lotId.toString()));
        assertEquals(new BigDecimal("3.25"), tx.executeAndReturn(conn -> movements.findInitialEntryByLotId(conn, lotId).orElseThrow()));
        assertThrows(SQLException.class, () -> exec("DELETE FROM stock_movement_items WHERE initial_entry_lot_id=?", lotId.toString()));
        assertThrows(SQLException.class, () -> {
            try (Connection c = DatabaseConnection.getConnection(); var statement = c.createStatement()) {
                statement.execute("SET @marketcontrol_allow_origin_delete=1");
                try (PreparedStatement delete = c.prepareStatement("DELETE FROM stock_movement_items WHERE initial_entry_lot_id=?")) {
                    delete.setString(1, lotId.toString());
                    delete.executeUpdate();
                }
            }
        }, "Application credentials must not bypass the trigger using the cleanup session variable");

        UUID duplicateMovementId = UUID.randomUUID();
        StockMovement duplicate = StockMovement.reconstitute(duplicateMovementId, MovementType.ENTRY, LocalDateTime.now(),
                "duplicate original", supplier,
                List.of(StockMovementItem.create(lotId, BigDecimal.ONE, new BigDecimal("9.99"))));
        assertThrows(PersistenceException.class, () -> tx.execute(conn -> movements.saveInitialEntry(conn, duplicate)));

        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement_items WHERE initial_entry_lot_id=?", lotId.toString()));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movements WHERE id=?", duplicateMovementId.toString()));
        assertEquals(new BigDecimal("3.25"), tx.executeAndReturn(conn -> movements.findInitialEntryByLotId(conn, lotId).orElseThrow()));
    }

    @Test void laterEntryDoesNotReplaceTheOriginalCost() throws Exception {
        UUID laterMovementId = UUID.randomUUID();
        StockMovement later = StockMovement.reconstitute(laterMovementId, MovementType.ENTRY, LocalDateTime.now(),
                "later replenishment", supplier,
                List.of(StockMovementItem.create(lotId, new BigDecimal("5"), new BigDecimal("9.99"))));
        tx.execute(conn -> movements.save(conn, later));

        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement_items WHERE initial_entry_lot_id=?", lotId.toString()));
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement_items WHERE stock_movement_id=? AND initial_entry_lot_id IS NULL", laterMovementId.toString()));
        assertEquals(new BigDecimal("3.25"), tx.executeAndReturn(conn -> movements.findInitialEntryByLotId(conn, lotId).orElseThrow()));
    }

    @Test void initialEntryCannotReferenceAnUnknownLotOrMarkAnExit() throws Exception {
        UUID noLotMovementId = UUID.randomUUID();
        StockMovement noLot = StockMovement.reconstitute(noLotMovementId, MovementType.ENTRY, LocalDateTime.now(),
                "unknown lot", supplier,
                List.of(StockMovementItem.create(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE)));
        assertThrows(PersistenceException.class, () -> tx.execute(conn -> movements.saveInitialEntry(conn, noLot)));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movements WHERE id=?", noLotMovementId.toString()));

        UUID exitMovementId = UUID.randomUUID();
        assertThrows(PersistenceException.class, () -> tx.execute(conn -> {
            try (PreparedStatement movement = conn.prepareStatement(
                    "INSERT INTO stock_movements (id,type,date_time,supplier_id,description) VALUES (?,2,?,NULL,?)")) {
                movement.setString(1, exitMovementId.toString());
                movement.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                movement.setString(3, "invalid initial marker");
                movement.executeUpdate();
            } catch (SQLException e) { throw new PersistenceException("Could not create invalid-marker fixture", e); }
            try (PreparedStatement item = conn.prepareStatement(
                    "INSERT INTO stock_movement_items (id,stock_movement_id,lot_id,quantity,unit_cost,initial_entry_lot_id) VALUES (?,?,?,?,?,?)")) {
                item.setString(1, UUID.randomUUID().toString());
                item.setString(2, exitMovementId.toString());
                item.setString(3, lotId.toString());
                item.setBigDecimal(4, BigDecimal.ONE);
                item.setBigDecimal(5, BigDecimal.ONE);
                item.setString(6, lotId.toString());
                item.executeUpdate();
            } catch (SQLException e) { throw new PersistenceException("Expected MariaDB to reject ENTRY marker on EXIT", e); }
        }));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movements WHERE id=?", exitMovementId.toString()));
    }

    @Test void invalidLotForeignKeyRollsBackMovementHeader() throws Exception {
        UUID invalidId = UUID.randomUUID();
        StockMovement invalid = StockMovement.reconstitute(invalidId, MovementType.EXIT, LocalDateTime.now(), "invalid lot FK", null,
                List.of(StockMovementItem.create(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE)));
        assertThrows(PersistenceException.class, () -> tx.execute(conn -> movements.save(conn, invalid)));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movements WHERE id=?", invalidId.toString()));
    }

    @Test void saleCommitsBalanceAndExitAndRollbackRestoresBalanceWhenSaveFails() throws Exception {
        JdbcLotRepository lots = new JdbcLotRepository(new JdbcProductRepository());
        StockMovementService service = new StockMovementService(tx, movements, lots);
        service.registerSale(lotId, new BigDecimal("2"));
        assertEquals(new BigDecimal("18.000"), scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=2 AND i.lot_id=?", lotId.toString()));
        assertEquals(new BigDecimal("3.25"), scalar("SELECT i.unit_cost FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=2 AND i.lot_id=?", lotId.toString()));

        StockMovementRepository failing = new StockMovementRepository() {
            public java.util.Optional<BigDecimal> findInitialEntryByLotId(Connection c, UUID id) { return movements.findInitialEntryByLotId(c, id); }
            public void save(Connection c, StockMovement movement) { throw new IllegalStateException("controlled failure after balance update"); }
            public void saveInitialEntry(Connection c, StockMovement movement) { throw new IllegalStateException("controlled initial entry failure"); }
        };
        StockMovementService failingService = new StockMovementService(tx, failing, lots);
        assertThrows(IllegalStateException.class, () -> failingService.registerSale(lotId, BigDecimal.ONE));
        assertEquals(new BigDecimal("18.000"), scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=2 AND i.lot_id=?", lotId.toString()));
        assertThrows(IllegalStateException.class, () -> failingService.registerLoss(lotId, BigDecimal.ONE, "controlled failure"));
        assertEquals(new BigDecimal("18.000"), scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=3 AND i.lot_id=?", lotId.toString()));
    }

    @Test void expiredLotAllowsLossAndInsufficientBalanceDoesNotUnderflow() throws Exception {
        exec("UPDATE lots SET expiration_date=? WHERE id=?", LocalDate.now().minusDays(1), lotId.toString());
        StockMovementService service = new StockMovementService(tx, movements, new JdbcLotRepository(new JdbcProductRepository()));
        service.registerLoss(lotId, BigDecimal.ONE, "expired");
        assertEquals(new BigDecimal("19.000"), scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
        assertEquals(new BigDecimal("3.25"), scalar("SELECT i.unit_cost FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=3 AND i.lot_id=?", lotId.toString()));
        assertThrows(IllegalArgumentException.class, () -> service.registerSale(lotId, BigDecimal.ONE));
        assertEquals(new BigDecimal("19.000"), scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
    }

    private static void exec(String sql, Object... args) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) p.setObject(i+1,args[i]); p.executeUpdate();
        }
    }
    private static void execUnchecked(String sql, Object... args) { try { exec(sql,args); } catch(SQLException e) { throw new AssertionError(e); } }
    private static int count(String sql, String... values) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            for (int i=0; i<values.length; i++) p.setString(i+1, values[i]);
            try(ResultSet rs=p.executeQuery()){rs.next(); return rs.getInt(1);}
        }
    }
    private static BigDecimal scalar(String sql, String value) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)) { p.setString(1,value); try(ResultSet rs=p.executeQuery()){rs.next(); return rs.getBigDecimal(1);} }
    }
}
