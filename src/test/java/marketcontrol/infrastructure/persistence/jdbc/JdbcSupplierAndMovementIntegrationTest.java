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
import marketcontrol.infrastructure.database.DatabaseConnection;
import marketcontrol.service.StockMovementService;

/** Real MariaDB tests. The lot fixture explicitly supplies the legacy NOT NULL column. */
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
        // Seed the physical lot row directly so ENTRY history can exercise movement persistence independently.
        exec("INSERT INTO lots (id,product_id,entry_date,expiration_date,available_quantity) VALUES (?,?,?,?,?)", lotId.toString(), productId.toString(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), new BigDecimal("20"));
        fixtureEntryId = UUID.randomUUID();
        tx.execute(conn -> movements.save(conn, StockMovement.reconstitute(fixtureEntryId, MovementType.ENTRY, LocalDateTime.now(), "fixture initial acquisition", supplier, List.of(StockMovementItem.create(lotId, new BigDecimal("20"), new BigDecimal("3.25"))))));
    }

    @AfterEach void cleanUp() throws Exception {
        if (lotId != null) {
            List<String> movementIds = new java.util.ArrayList<>();
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("SELECT DISTINCT stock_movement_id FROM stock_movement_items WHERE lot_id=?")) {
                p.setString(1, lotId.toString()); try (ResultSet rs = p.executeQuery()) { while (rs.next()) movementIds.add(rs.getString(1)); }
            }
            exec("DELETE FROM stock_movement_items WHERE lot_id = ?", lotId.toString());
            for (String movementId : movementIds) exec("DELETE FROM stock_movements WHERE id = ?", movementId);
            exec("DELETE FROM lots WHERE id = ?", lotId.toString());
        }
        if (supplierId != null) exec("DELETE FROM suppliers WHERE id = ?", supplierId.toString());
        if (extraSupplierId != null) exec("DELETE FROM suppliers WHERE id = ?", extraSupplierId.toString());
        if (productId != null) exec("DELETE FROM products WHERE id = ?", productId.toString());
        if (categoryId != null) exec("DELETE FROM categories WHERE id = ?", categoryId.toString());
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

        StockMovementRepository failing = new StockMovementRepository() {
            public java.util.Optional<BigDecimal> findInitialEntryByLotId(Connection c, UUID id) { return movements.findInitialEntryByLotId(c, id); }
            public void save(Connection c, StockMovement movement) { throw new IllegalStateException("controlled failure after balance update"); }
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
        assertThrows(IllegalArgumentException.class, () -> service.registerSale(lotId, BigDecimal.ONE));
        assertEquals(new BigDecimal("19.000"), scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
    }

    private static void exec(String sql, Object... args) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i=0;i<args.length;i++) p.setObject(i+1,args[i]); p.executeUpdate();
        }
    }
    private static void execUnchecked(String sql, Object... args) { try { exec(sql,args); } catch(SQLException e) { throw new AssertionError(e); } }
    private static int count(String sql, String value) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)) { p.setString(1,value); try(ResultSet rs=p.executeQuery()){rs.next(); return rs.getInt(1);} }
    }
    private static BigDecimal scalar(String sql, String value) throws SQLException {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)) { p.setString(1,value); try(ResultSet rs=p.executeQuery()){rs.next(); return rs.getBigDecimal(1);} }
    }
}
