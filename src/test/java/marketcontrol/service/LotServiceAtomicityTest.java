package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.FakeTransactionManager;
import marketcontrol.transaction.TransactionManager;

/** Service tests using fakes. They do not exercise JDBC or MariaDB transactions. */
class LotServiceAtomicityTest {
    @Test void passesTheSameConnectionToLotAndMovementRepositories() {
        UUID lotId = UUID.randomUUID();
        AtomicReference<Connection> lotConnection = new AtomicReference<>();
        AtomicReference<Connection> movementConnection = new AtomicReference<>();
        Connection connection = proxyConnection();
        FakeLots lots = new FakeLots(lotId, lotConnection, 1);
        StockMovementRepository movements = movementRepository(movementConnection::set, false);
        StockMovementService service = new StockMovementService(new FakeTransactionManager(connection), movements, lots);

        service.registerSale(lotId, new BigDecimal("10"));

        assertSame(connection, lotConnection.get());
        assertSame(connection, movementConnection.get());
        assertEquals(0, new BigDecimal("90").compareTo(lots.available.get()));
    }

    @Test void restoresFakeRepositoryStateWhenMovementSaveFails() {
        UUID lotId = UUID.randomUUID();
        AtomicReference<Connection> capturedConnection = new AtomicReference<>();
        AtomicBoolean rollbackPath = new AtomicBoolean();
        Connection connection = proxyConnection();
        FakeLots lots = new FakeLots(lotId, capturedConnection, 1);
        StockMovementRepository movements = movementRepository(ignored -> {}, true);
        TransactionManager simulatedTransaction = new TransactionManager() {
            @Override public <T> T executeAndReturn(marketcontrol.transaction.TransactionAction<T> action) {
                return action.execute(connection);
            }
            @Override public void execute(marketcontrol.transaction.TransactionWork work) {
                BigDecimal before = lots.available.get();
                try {
                    work.execute(connection);
                } catch (RuntimeException failure) {
                    lots.available.set(before);
                    rollbackPath.set(true);
                    throw failure;
                }
            }
        };
        StockMovementService service = new StockMovementService(simulatedTransaction, movements, lots);

        assertThrows(IllegalStateException.class, () -> service.registerSale(lotId, new BigDecimal("10")));

        assertTrue(rollbackPath.get());
        assertEquals(0, new BigDecimal("100").compareTo(lots.available.get()));
    }

    @Test void rejectsSaleWhenLotUpdateReportsNoAffectedRow() {
        UUID lotId = UUID.randomUUID();
        AtomicReference<Connection> capturedConnection = new AtomicReference<>();
        AtomicBoolean movementSaved = new AtomicBoolean();
        FakeLots lots = new FakeLots(lotId, capturedConnection, 0);
        StockMovementRepository movements = movementRepository(ignored -> movementSaved.set(true), false);
        StockMovementService service = new StockMovementService(new FakeTransactionManager(proxyConnection()), movements, lots);

        assertThrows(IllegalArgumentException.class, () -> service.registerSale(lotId, BigDecimal.ONE));

        assertFalse(movementSaved.get());
        assertEquals(0, new BigDecimal("100").compareTo(lots.available.get()));
    }

    @Test void rejectsInvalidSaleBeforeExecutingTransaction() {
        AtomicBoolean transactionStarted = new AtomicBoolean();
        FakeLots lots = new FakeLots(UUID.randomUUID(), new AtomicReference<>(), 1);
        StockMovementService service = new StockMovementService(new TransactionManager() {
            @Override public <T> T executeAndReturn(marketcontrol.transaction.TransactionAction<T> action) {
                transactionStarted.set(true);
                return action.execute(proxyConnection());
            }
            @Override public void execute(marketcontrol.transaction.TransactionWork work) {
                transactionStarted.set(true);
                work.execute(proxyConnection());
            }
        }, movementRepository(ignored -> {}, false), lots);

        assertThrows(IllegalArgumentException.class, () -> service.registerSale(lots.lotId, BigDecimal.ZERO));
        assertFalse(transactionStarted.get());
    }

    private static Connection proxyConnection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> null);
    }

    private static StockMovementRepository movementRepository(java.util.function.Consumer<Connection> saved, boolean failSave) {
        return new StockMovementRepository() {
            public void save(Connection conn, StockMovement movement) {
                if (failSave) throw new IllegalStateException("simulated movement repository failure");
                saved.accept(conn);
            }
            public void saveInitialEntry(Connection conn, StockMovement movement) {
                if (failSave) throw new IllegalStateException("simulated movement repository failure");
                saved.accept(conn);
            }
            public Optional<BigDecimal> findInitialEntryByLotId(Connection conn, UUID lotId) {
                return Optional.of(new BigDecimal("2.50"));
            }
        };
    }

    private static final class FakeLots implements LotRepository {
        private final UUID lotId;
        private final AtomicReference<Connection> connectionCapture;
        private final int affectedRows;
        private final AtomicReference<BigDecimal> available = new AtomicReference<>(new BigDecimal("100"));

        private FakeLots(UUID lotId, AtomicReference<Connection> connectionCapture, int affectedRows) {
            this.lotId = lotId;
            this.connectionCapture = connectionCapture;
            this.affectedRows = affectedRows;
        }

        public void saveWithInitialEntry(Connection conn, marketcontrol.domain.model.Lot lot,
                marketcontrol.domain.model.interfaces.ReadOnlySupplier supplier, BigDecimal unitCost) {}
        public Optional<marketcontrol.domain.model.Lot> findById(UUID id) { return Optional.empty(); }
        public Optional<ReadOnlyLot> findById(Connection conn, UUID id) { return Optional.empty(); }
        public List<marketcontrol.domain.model.Lot> findByProductId(UUID productId) { return List.of(); }
        public BigDecimal getTotalAvailableQuantity(UUID productId) { return available.get(); }

        public int decreaseAvailableQuantityForSale(Connection conn, UUID id, BigDecimal quantity) {
            connectionCapture.set(conn);
            if (affectedRows == 1) available.updateAndGet(value -> value.subtract(quantity));
            return affectedRows;
        }

        public int decreaseAvailableQuantityForLoss(Connection conn, UUID id, BigDecimal quantity) { return 0; }
        public int increaseAvailableQuantityForAdditionalEntry(Connection conn, UUID id, BigDecimal quantity) { return 0; }
    }
}
