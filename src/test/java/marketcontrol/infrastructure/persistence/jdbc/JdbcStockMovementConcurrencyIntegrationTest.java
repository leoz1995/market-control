package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.http.server.infrastructure.database.DatabaseConnection;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcLotRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcStockMovementRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcSupplierRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcTransactionManager;
import marketcontrol.service.CategoryService;
import marketcontrol.service.ProductService;
import marketcontrol.service.StockMovementService;
import marketcontrol.transaction.TransactionAction;
import marketcontrol.transaction.TransactionManager;
import marketcontrol.transaction.TransactionWork;

/** Real concurrent transactions against independent MariaDB connections. */
class JdbcStockMovementConcurrencyIntegrationTest {
    private final String run = UUID.randomUUID().toString();
    private UUID categoryId;
    private UUID productId;
    private UUID lotId;
    private UUID supplierId;
    private final JdbcStockMovementRepository movements = new JdbcStockMovementRepository();
    private final JdbcLotRepository lots = new JdbcLotRepository(new JdbcProductRepository());

    @BeforeEach void setUp() throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); var s = c.createStatement(); var rs = s.executeQuery("SELECT DATABASE()")) {
            assertTrue(rs.next());
            assertEquals("marketcontrol", rs.getString(1), "Concurrency tests may only use marketcontrol test database");
        }
        var categories = new CategoryService(new JdbcCategoryRepository());
        var category = categories.saveCategory("Concurrency " + run, "Concurrent stock fixture", true);
        categoryId = category.getId();
        var products = new ProductService(new JdbcProductRepository(), categories);
        productId = products.saveProduct("Concurrent product", "CONC-" + run, null, categoryId, UnitOfMeasure.UNIT).getId();
        Supplier supplier = Supplier.create("Concurrency supplier", "Concurrency supplier", document(run), "conc-" + run + "@example.invalid", "555", "Test");
        supplierId = supplier.getId();
        new JdbcSupplierRepository().save(supplier);
        Lot lot = Lot.create(products.findById(productId), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("10"));
        lotId = lot.getId();
        JdbcTransactionManager tx = new JdbcTransactionManager();
        tx.execute(conn -> lots.saveWithInitialEntry(conn, lot, supplier, new BigDecimal("3.25")));
    }

    @AfterEach void cleanUp() {
        DatabaseTestCleanup.deleteCategory(categoryId);
        if (supplierId != null) {
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM suppliers WHERE id=?")) {
                p.setString(1, supplierId.toString()); p.executeUpdate();
            } catch (Exception e) { throw new AssertionError("Could not clean concurrency supplier fixture", e); }
        }
    }

    @RepeatedTest(10)
    void twoConcurrentSalesOfSevenCannotOversellTen() throws Exception {
        ConcurrentRun run = concurrent(false, service -> {
            service.registerSale(lotId, new BigDecimal("7"));
            return null;
        }, service -> {
            service.registerSale(lotId, new BigDecimal("7"));
            return null;
        });
        assertTwoIndependentConnections(run);
        assertEquals(1, successes(run));
        assertEquals(1, failures(run));
        assertTrue(run.failures().stream().filter(java.util.Objects::nonNull).allMatch(IllegalArgumentException.class::isInstance));
        assertDatabaseState(new BigDecimal("3.000"), 1, 0);
    }

    @RepeatedTest(10)
    void concurrentSaleAndLossCannotOversellTen() throws Exception {
        ConcurrentRun run = concurrent(false,
                service -> { service.registerSale(lotId, new BigDecimal("7")); return null; },
                service -> { service.registerLoss(lotId, new BigDecimal("7"), "concurrent loss"); return null; });
        assertTwoIndependentConnections(run);
        assertEquals(1, successes(run));
        assertEquals(1, failures(run));
        assertTrue(run.failures().stream().filter(java.util.Objects::nonNull).allMatch(IllegalArgumentException.class::isInstance));
        int exits = countSuccess(run, MovementType.EXIT);
        int losses = countSuccess(run, MovementType.LOSS);
        assertTrue((exits == 1 && losses == 0) || (exits == 0 && losses == 1));
        assertDatabaseState(new BigDecimal("3.000"), exits, losses);
    }

    @RepeatedTest(10)
    void concurrentOperationsWithinBalanceBothCommit() throws Exception {
        ConcurrentRun run = concurrent(false,
                service -> { service.registerSale(lotId, new BigDecimal("4")); return null; },
                service -> { service.registerLoss(lotId, new BigDecimal("5"), "concurrent loss"); return null; });
        assertTwoIndependentConnections(run);
        assertEquals(2, successes(run));
        assertEquals(0, failures(run));
        assertDatabaseState(new BigDecimal("1.000"), 1, 1);
    }

    @RepeatedTest(10)
    void failedTransactionRollsBackWithoutUndoingConcurrentSuccessfulSale() throws Exception {
        ConcurrentRun run = concurrent(true,
                service -> { service.registerSale(lotId, new BigDecimal("4")); return null; },
                service -> { service.registerSale(lotId, new BigDecimal("4")); return null; });
        assertTwoIndependentConnections(run);
        assertEquals(1, successes(run));
        assertEquals(1, failures(run));
        assertTrue(run.failures().stream().filter(java.util.Objects::nonNull).allMatch(IllegalStateException.class::isInstance));
        assertDatabaseState(new BigDecimal("6.000"), 1, 0);
    }

    private ConcurrentRun concurrent(boolean failOneMovementSave, Operation first, Operation second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.ConcurrentLinkedQueue<Long> connectionIds = new java.util.concurrent.ConcurrentLinkedQueue<>();
        TransactionManager synchronizedTransactions = new SynchronizedJdbcTransactions(ready, release, connectionIds);
        ThreadLocal<Boolean> failThisSave = ThreadLocal.withInitial(() -> false);
        StockMovementRepository concurrentMovements = new StockMovementRepository() {
            @Override public void save(Connection c, StockMovement movement) {
                if (failThisSave.get()) throw new IllegalStateException("controlled movement insert failure after balance update");
                movements.save(c, movement);
            }
            @Override public void saveInitialEntry(Connection c, StockMovement movement) { movements.saveInitialEntry(c, movement); }
            @Override public java.util.Optional<BigDecimal> findInitialEntryByLotId(Connection c, UUID id) {
                return movements.findInitialEntryByLotId(c, id);
            }
        };
        StockMovementService service = new StockMovementService(synchronizedTransactions, concurrentMovements, lots);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Throwable> a = executor.submit(() -> executeOperation(first, service, false, failThisSave, failOneMovementSave));
            Future<Throwable> b = executor.submit(() -> executeOperation(second, service, failOneMovementSave, failThisSave, failOneMovementSave));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both transactions must open independent connections before release");
            release.countDown();
            return new ConcurrentRun(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS), List.copyOf(connectionIds));
        } finally {
            release.countDown();
        }
    }

    private static Throwable executeOperation(Operation operation, StockMovementService service, boolean injectFailure,
            ThreadLocal<Boolean> failThisSave, boolean failureTest) {
        failThisSave.set(failureTest && injectFailure);
        try { operation.run(service); return null; }
        catch (Throwable failure) { return failure; }
        finally { failThisSave.remove(); }
    }

    private void assertTwoIndependentConnections(ConcurrentRun run) {
        assertEquals(2, run.connectionIds().size());
        assertNotEquals(run.connectionIds().get(0), run.connectionIds().get(1), "Each thread must use a different MariaDB connection");
    }

    private int successes(ConcurrentRun run) { return (run.firstFailure() == null ? 1 : 0) + (run.secondFailure() == null ? 1 : 0); }
    private int failures(ConcurrentRun run) { return (run.firstFailure() == null ? 0 : 1) + (run.secondFailure() == null ? 0 : 1); }

    private int countSuccess(ConcurrentRun run, MovementType type) throws SQLException {
        return count("SELECT COUNT(*) FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=? AND i.lot_id=?", type.getCode(), lotId.toString());
    }

    private void assertDatabaseState(BigDecimal expectedBalance, int expectedExits, int expectedLosses) throws SQLException {
        assertEquals(expectedBalance, scalar("SELECT available_quantity FROM lots WHERE id=?", lotId.toString()));
        assertEquals(0, scalar("SELECT COUNT(*) FROM lots WHERE id=? AND available_quantity<0", lotId.toString()).intValue());
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE sm.type=1 AND i.initial_entry_lot_id=?", lotId.toString()));
        assertEquals(expectedExits, count("SELECT COUNT(*) FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE sm.type=2 AND i.lot_id=?", lotId.toString()));
        assertEquals(expectedLosses, count("SELECT COUNT(*) FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE sm.type=3 AND i.lot_id=?", lotId.toString()));
        assertEquals(1 + expectedExits + expectedLosses, count("SELECT COUNT(*) FROM stock_movement_items WHERE lot_id=?", lotId.toString()));
        assertEquals(1 + expectedExits + expectedLosses, count("SELECT COUNT(*) FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE i.lot_id=?", lotId.toString()));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE sm.type IN (2,3) AND i.lot_id=? AND i.unit_cost<>3.25", lotId.toString()));
    }

    private static int count(String sql, Object... values) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i=0;i<values.length;i++) p.setObject(i+1, values[i]);
            try (ResultSet rs = p.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static BigDecimal scalar(String sql, Object... values) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i=0;i<values.length;i++) p.setObject(i+1, values[i]);
            try (ResultSet rs = p.executeQuery()) { assertTrue(rs.next()); return rs.getBigDecimal(1); }
        }
    }

    private static String document(String value) {
        long numeric = Math.floorMod(UUID.fromString(value).getMostSignificantBits(), 100_000_000_000_000L);
        return String.format("%014d", numeric);
    }

    @FunctionalInterface private interface Operation { Object run(StockMovementService service); }
    private record ConcurrentRun(Throwable firstFailure, Throwable secondFailure, List<Long> connectionIds) {
        List<Throwable> failures() { return java.util.Arrays.asList(firstFailure, secondFailure); }
    }

    private static final class SynchronizedJdbcTransactions implements TransactionManager {
        private final CountDownLatch ready;
        private final CountDownLatch release;
        private final java.util.Queue<Long> connectionIds;
        private SynchronizedJdbcTransactions(CountDownLatch ready, CountDownLatch release, java.util.Queue<Long> connectionIds) {
            this.ready = ready; this.release = release; this.connectionIds = connectionIds;
        }
        @Override public void execute(TransactionWork work) { executeAndReturn(c -> { work.execute(c); return null; }); }
        @Override public <T> T executeAndReturn(TransactionAction<T> action) {
            try (Connection c = DatabaseConnection.getConnection()) {
                c.setAutoCommit(false);
                try {
                    try (var s = c.createStatement(); var rs = s.executeQuery("SELECT CONNECTION_ID()")) {
                        assertTrue(rs.next()); connectionIds.add(rs.getLong(1));
                    }
                    ready.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test start timed out");
                    T result = action.execute(c);
                    c.commit();
                    return result;
                } catch (Throwable failure) {
                    try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                    if (failure instanceof RuntimeException runtime) throw runtime;
                    if (failure instanceof Error error) throw error;
                    throw new IllegalStateException("Concurrent transaction failed", failure);
                }
            } catch (SQLException e) { throw new IllegalStateException("Could not open concurrent MariaDB transaction", e); }
        }
    }
}
