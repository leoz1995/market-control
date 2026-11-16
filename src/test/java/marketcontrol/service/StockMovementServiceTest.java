package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.FakeTransactionManager;

class StockMovementServiceTest {
    @Test void additionalEntryUpdatesLotAndSavesMovementOnSameTransactionConnection() {
        UUID lotId = UUID.randomUUID();
        Connection connection = proxyConnection();
        FakeLots lots = new FakeLots(1);
        Lot updatedLot = Lot.reconstitute(lotId,
                Product.create("Product", "BARCODE-" + lotId, null, Category.create("Category", "Test category description", true), UnitOfMeasure.UNIT),
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(3), new BigDecimal("15"), new BigDecimal("15"));
        lots.lotToReturn = updatedLot;
        AtomicReference<Connection> updatedOn = new AtomicReference<>();
        lots.connectionSeen = updatedOn;
        AtomicReference<Connection> savedOn = new AtomicReference<>();
        AtomicReference<StockMovement> savedMovement = new AtomicReference<>();
        StockMovementRepository movements = (conn, movement) -> { savedOn.set(conn); savedMovement.set(movement); };
        StockMovementService service = new StockMovementService(new FakeTransactionManager(connection), movements, lots);

        ReadOnlyLot returnedLot = service.registerAdditionalEntry(lotId, new BigDecimal("5"));

        assertSame(connection, updatedOn.get());
        assertSame(connection, savedOn.get());
        assertSame(connection, lots.lookupConnection.get(), "Read after update should share the transaction connection");
        assertSame(updatedLot, returnedLot);
        assertEquals(MovementType.ENTRY, savedMovement.get().getType());
        assertEquals(lotId, savedMovement.get().getItems().get(0).getLotId());
        assertEquals(0, new BigDecimal("5").compareTo(savedMovement.get().getItems().get(0).getQuantity()));
    }

    @Test void additionalEntryDoesNotSaveMovementWhenLotUpdateAffectsNoRows() {
        FakeLots lots = new FakeLots(0);
        AtomicReference<StockMovement> savedMovement = new AtomicReference<>();
        StockMovementService service = new StockMovementService(new FakeTransactionManager(proxyConnection()),
                (conn, movement) -> savedMovement.set(movement), lots);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerAdditionalEntry(UUID.randomUUID(), BigDecimal.ONE));
        assertNull(savedMovement.get());
    }

    @Test void lossUsesTransactionAndSavesLossMovement() {
        UUID lotId = UUID.randomUUID();
        AtomicReference<StockMovement> savedMovement = new AtomicReference<>();
        AtomicReference<Connection> lossConnection = new AtomicReference<>();
        AtomicReference<Connection> savedConnection = new AtomicReference<>();
        FakeLots lots = new FakeLots(1);
        lots.connectionSeen = lossConnection;
        StockMovementRepository movements = (conn, movement) -> { savedConnection.set(conn); savedMovement.set(movement); };
        Connection connection = proxyConnection();
        StockMovementService service = new StockMovementService(new FakeTransactionManager(connection), movements, lots);

        service.registerLoss(lotId, new BigDecimal("2"), "Validade vencida");

        assertSame(connection, lossConnection.get());
        assertSame(connection, savedConnection.get());
        assertEquals(MovementType.LOSS, savedMovement.get().getType());
        assertEquals("Validade vencida", savedMovement.get().getDescription());
    }

    @Test void rejectsInvalidLossQuantityBeforeStartingTransaction() {
        AtomicReference<Boolean> transactionStarted = new AtomicReference<>(false);
        FakeLots lots = new FakeLots(1);
        StockMovementService service = new StockMovementService(new marketcontrol.transaction.TransactionManager() {
            @Override public <T> T executeAndReturn(marketcontrol.transaction.TransactionAction<T> operation) {
                transactionStarted.set(true);
                return operation.execute(proxyConnection());
            }
            @Override public void execute(marketcontrol.transaction.TransactionWork work) {
                transactionStarted.set(true);
                work.execute(proxyConnection());
            }
        }, (conn, movement) -> {}, lots);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerLoss(UUID.randomUUID(), BigDecimal.ZERO, "Causa de teste"));
        assertFalse(transactionStarted.get());
    }

    private static Connection proxyConnection() {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> null);
    }

    private static final class FakeLots implements LotRepository {
        private final int affectedRows;
        private AtomicReference<Connection> connectionSeen;
        private final AtomicReference<Connection> lookupConnection = new AtomicReference<>();
        private ReadOnlyLot lotToReturn;
        private FakeLots(int affectedRows) { this.affectedRows = affectedRows; }
        public void save(Connection conn, Lot lot) {}
        public Optional<Lot> findById(UUID id) { return Optional.empty(); }
        public Optional<ReadOnlyLot> findById(Connection connection, UUID id) {
            lookupConnection.set(connection);
            return Optional.ofNullable(lotToReturn);
        }
        public List<Lot> findByProductId(UUID id) { return List.of(); }
        public BigDecimal getTotalAvailableQuantity(UUID productId) { return BigDecimal.ZERO; }
        public int decreaseAvailableQuantityForSale(Connection conn, UUID id, BigDecimal quantity) { if (connectionSeen != null) connectionSeen.set(conn); return affectedRows; }
        public int decreaseAvailableQuantityForLoss(Connection conn, UUID id, BigDecimal quantity) { if (connectionSeen != null) connectionSeen.set(conn); return affectedRows; }
        public int increaseAvailableQuantityForAdditionalEntry(Connection conn, UUID id, BigDecimal quantity) { if (connectionSeen != null) connectionSeen.set(conn); return affectedRows; }
    }
}
