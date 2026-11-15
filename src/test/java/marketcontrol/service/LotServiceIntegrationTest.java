package marketcontrol.service;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.StockMovementItem;
import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.infrastructure.database.DatabaseConnection;
import marketcontrol.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcLotRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcStockMovementRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcTransactionManager;
import marketcontrol.exception.PersistenceException;
import marketcontrol.transaction.TransactionManager;

class LotServiceIntegrationTest {
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private CategoryService categories;
    private ProductService products;
    private LotService lots;
    private LotRepository lotRepository;
    private StockMovementRepository movementRepository;
    private TransactionManager transactionManager;
    private ReadOnlyCategory category;
    private ReadOnlyProduct product;

    @BeforeEach void setUp() {
        categories = new CategoryService(new JdbcCategoryRepository());
        JdbcProductRepository productRepository = new JdbcProductRepository();
        products = new ProductService(productRepository, categories);
        lotRepository = new JdbcLotRepository(productRepository);
        movementRepository = new JdbcStockMovementRepository();
        transactionManager = new JdbcTransactionManager();
        StockMovementService movements = new StockMovementService(transactionManager, movementRepository, lotRepository);
        lots = new LotService(products, lotRepository, transactionManager, movements);
        category = categories.saveCategory("Lot test " + run, null, true);
        product = products.saveProduct("Test product", run, null, category.getId(), UnitOfMeasure.UNIT);
    }

    @AfterEach void cleanUp() {
        if (category != null) DatabaseTestCleanup.deleteCategory(category.getId());
    }

    @Test void lotCreationPersistsOnlyInitialQuantityAndEntryMovement() {
        LocalDate today = LocalDate.now();
        ReadOnlyLot lot = lots.save(product.getId(), today, today.plusDays(20), new BigDecimal("10.000"));

        assertAll(
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(persistedQuantity(lot.getId())),
                        "Creating a lot with 10 units should persist 10 units, not count the initial quantity twice"),
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(persistedLotState(lot.getId()).totalReceived()),
                        "The initial receipt must persist as total_received_quantity"),
                () -> assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                () -> assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(
                        movementItemQuantityForLotAndType(lot.getId(), MovementType.ENTRY.getCode()))),
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(lots.findById(lot.getId()).getAvailableQuantity()),
                        "The persisted lot should be reconstitutable with its initial quantity"));
    }

    @Test void rollsBackInitialLotWhenInitialMovementPersistenceFails() {
        AtomicReference<UUID> savedLotId = new AtomicReference<>();
        AtomicReference<UUID> savedMovementId = new AtomicReference<>();
        LotRepository capturingLotRepository = new JdbcLotRepository(new JdbcProductRepository()) {
            @Override public void save(Connection connection, Lot lot) {
                super.save(connection, lot);
                savedLotId.set(lot.getId());
            }
        };
        StockMovementRepository failAfterWritingInitialMovement = (connection, movement) -> {
            savedMovementId.set(movement.getId());
            new JdbcStockMovementRepository().save(connection, movement);
            throw new IllegalStateException("forced initial movement persistence failure");
        };
        StockMovementService failingMovementService = new StockMovementService(transactionManager,
                failAfterWritingInitialMovement, capturingLotRepository);
        LotService failingLotService = new LotService(products, capturingLotRepository, transactionManager,
                failingMovementService);

        assertThrows(IllegalStateException.class, () -> failingLotService.save(product.getId(), LocalDate.now(),
                LocalDate.now().plusDays(20), new BigDecimal("10.000")));

        UUID lotId = savedLotId.get();
        UUID movementId = savedMovementId.get();
        assertNotNull(lotId, "Lot repository should have received the attempted lot");
        assertNotNull(movementId, "Movement repository should have received the attempted movement");
        assertAll(
                () -> assertTrue(lotRepository.findById(lotId).isEmpty(), "The initial lot insert must roll back"),
                () -> assertEquals(0, countById("SELECT COUNT(*) FROM stock_movements WHERE id = ?", movementId)),
                () -> assertEquals(0, countById("SELECT COUNT(*) FROM stock_movement_items WHERE stock_movement_id = ?", movementId)));
    }

    @Test void saleUpdatesPersistedStockAndWritesExitMovement() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));

        new StockMovementService(transactionManager, movementRepository, lotRepository)
                .registerSale(lot.getId(), new BigDecimal("3.000"));

        assertEquals(0, new BigDecimal("7.000").compareTo(persistedQuantity(lot.getId())));
        assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
        assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
    }

    @Test void additionalEntryUpdatesReceivedAndAvailableQuantitiesAndRecordsMovement() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        new StockMovementService(transactionManager, movementRepository, lotRepository)
                .registerSale(lot.getId(), new BigDecimal("3.000"));

        ReadOnlyLot updated = lots.updateStockLot(lot.getId(), new BigDecimal("5.000"));
        LotState state = persistedLotState(lot.getId());

        assertAll(
                () -> assertEquals(0, new BigDecimal("15.000").compareTo(state.totalReceived()),
                        "Additional receipt must increase total_received_quantity"),
                () -> assertEquals(0, new BigDecimal("12.000").compareTo(state.available()),
                        "Additional receipt must increase the remaining available balance"),
                () -> assertEquals(0, new BigDecimal("12.000").compareTo(updated.getAvailableQuantity())),
                () -> assertEquals(2, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                () -> assertEquals(2, movementItemCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                () -> assertEquals(0, new BigDecimal("15.000").compareTo(
                        movementItemQuantitySumForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                        "Initial and additional entry items must reconcile with total received quantity"));
    }

    @Test void additionalEntryRollsBackLotAndMovementWhenMovementPersistenceFails() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        LotState before = persistedLotState(lot.getId());
        StockMovementRepository failAfterWritingMovement = (connection, movement) -> {
            new JdbcStockMovementRepository().save(connection, movement);
            throw new IllegalStateException("forced failure after writing movement and items");
        };
        StockMovementService failingService = new StockMovementService(transactionManager,
                failAfterWritingMovement, lotRepository);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> failingService.registerAdditionalEntry(lot.getId(), new BigDecimal("5.000")));

        LotState after = persistedLotState(lot.getId());
        assertAll(
                () -> assertEquals("forced failure after writing movement and items", failure.getMessage()),
                () -> assertEquals(0, before.totalReceived().compareTo(after.totalReceived()),
                        "Rollback must restore total_received_quantity"),
                () -> assertEquals(0, before.available().compareTo(after.available()),
                        "Rollback must restore available_quantity"),
                () -> assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode()),
                        "The partially written additional movement must be rolled back"),
                () -> assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode()),
                        "The partially written additional movement item must be rolled back"));
    }

    @Test void rejectsAdditionalEntryForExpiredOrMissingLotWithoutRecordingMovement() {
        LocalDate expiredEntry = LocalDate.now().minusDays(3);
        ReadOnlyLot expired = lots.save(product.getId(), expiredEntry, LocalDate.now().minusDays(1), new BigDecimal("4"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);
        UUID missingLotId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.registerAdditionalEntry(expired.getId(), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerAdditionalEntry(missingLotId, BigDecimal.ONE));

        assertEquals(1, movementCountForLotAndType(expired.getId(), MovementType.ENTRY.getCode()));
        assertEquals(0, movementCountForLotAndType(missingLotId, MovementType.ENTRY.getCode()));
    }

    @Test void rejectsInvalidAdditionalEntryWithoutChangingPersistedLot() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        LotState before = persistedLotState(lot.getId());
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerAdditionalEntry(lot.getId(), BigDecimal.ZERO));

        LotState after = persistedLotState(lot.getId());
        assertEquals(0, before.totalReceived().compareTo(after.totalReceived()));
        assertEquals(0, before.available().compareTo(after.available()));
        assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode()));
    }

    @Test void rollsBackDatabaseStockWhenMovementRepositoryFails() {
        LocalDate today = LocalDate.now();
        ReadOnlyLot lot = lots.save(product.getId(), today, today.plusDays(20), new BigDecimal("100"));
        BigDecimal persistedBefore = persistedQuantity(lot.getId());
        StockMovementRepository failingMovements = (connection, movement) -> {
            throw new IllegalStateException("forced movement repository failure");
        };
        StockMovementService failingMovementService = new StockMovementService(transactionManager, failingMovements, lotRepository);

        assertThrows(IllegalStateException.class,
                () -> failingMovementService.registerSale(lot.getId(), new BigDecimal("10")));

        assertEquals(0, persistedBefore.compareTo(persistedQuantity(lot.getId())));
        assertEquals(0, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
        assertEquals(0, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
    }

    @Test void saleEqualToAvailableBalanceLeavesValidExhaustedLot() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));

        new StockMovementService(transactionManager, movementRepository, lotRepository)
                .registerSale(lot.getId(), new BigDecimal("10.000"));

        LotState state = persistedLotState(lot.getId());
        assertAll(
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(state.totalReceived())),
                () -> assertEquals(0, BigDecimal.ZERO.compareTo(state.available())),
                () -> assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode())),
                () -> assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode())),
                () -> assertEquals(0, BigDecimal.ZERO.compareTo(lots.findById(lot.getId()).getAvailableQuantity())));
    }

    @Test void doesNotPersistMovementWhenLotUpdateFails() {
        LocalDate today = LocalDate.now();
        ReadOnlyLot lot = lots.save(product.getId(), today, today.plusDays(20), new BigDecimal("100"));
        BigDecimal persistedBefore = persistedQuantity(lot.getId());
        LotRepository failingLots = new JdbcLotRepository(new JdbcProductRepository()) {
            @Override public int decreaseAvailableQuantityForSale(Connection connection, UUID lotId, BigDecimal quantity) {
                throw new IllegalStateException("forced lot update failure");
            }
        };
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, failingLots);

        assertThrows(IllegalStateException.class, () -> service.registerSale(lot.getId(), new BigDecimal("10")));

        assertEquals(0, persistedBefore.compareTo(persistedQuantity(lot.getId())));
        assertEquals(0, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
        assertEquals(0, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
    }

    @Test void concurrentSalesCannotConsumeMoreThanAvailableStock() throws Exception {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> attemptSale(service, lot.getId(), ready, start));
            Future<Boolean> second = executor.submit(() -> attemptSale(service, lot.getId(), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both sales should reach the start barrier");
            start.countDown();

            boolean firstSucceeded = first.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(10, TimeUnit.SECONDS);
            LotState state = persistedLotState(lot.getId());

            assertNotEquals(firstSucceeded, secondSucceeded,
                    "Exactly one sale of 7 should succeed against available stock of 10");
            assertEquals(0, new BigDecimal("3.000").compareTo(state.available()));
            assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
            assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
            assertEquals(0, new BigDecimal("7.000").compareTo(
                    movementItemQuantitySumForLotAndType(lot.getId(), MovementType.EXIT.getCode())));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void concurrentSalesWhoseSumEqualsBalanceBothSucceed() throws Exception {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> attemptSale(service, lot.getId(), new BigDecimal("4.000"), ready, start));
            Future<Boolean> second = executor.submit(() -> attemptSale(service, lot.getId(), new BigDecimal("6.000"), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both sales should reach the start barrier");
            start.countDown();

            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));
            LotState state = persistedLotState(lot.getId());
            assertAll(
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(state.available())),
                    () -> assertEquals(2, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode())),
                    () -> assertEquals(2, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode())),
                    () -> assertEquals(0, new BigDecimal("10.000").compareTo(
                            movementItemQuantitySumForLotAndType(lot.getId(), MovementType.EXIT.getCode()))));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void concurrentAdditionalEntriesAccumulateTotalAvailableAndHistory() throws Exception {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> attemptAdditionalEntry(service, lot.getId(), new BigDecimal("3.000"), ready, start));
            Future<?> second = executor.submit(() -> attemptAdditionalEntry(service, lot.getId(), new BigDecimal("5.000"), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both entries should reach the start barrier");
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            LotState state = persistedLotState(lot.getId());
            assertAll(
                    () -> assertEquals(0, new BigDecimal("18.000").compareTo(state.totalReceived())),
                    () -> assertEquals(0, new BigDecimal("18.000").compareTo(state.available())),
                    () -> assertEquals(3, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                    () -> assertEquals(3, movementItemCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                    () -> assertEquals(0, new BigDecimal("18.000").compareTo(
                            movementItemQuantitySumForLotAndType(lot.getId(), MovementType.ENTRY.getCode()))));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void concurrentAdditionalEntryAndSalePreserveSerializedStockAndHistory() throws Exception {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> sale = executor.submit(() -> attemptSale(service, lot.getId(), new BigDecimal("12.000"), ready, start));
            Future<?> entry = executor.submit(() -> attemptAdditionalEntry(service, lot.getId(), new BigDecimal("5.000"), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Entry and sale should reach the start barrier");
            start.countDown();
            boolean saleSucceeded = sale.get(10, TimeUnit.SECONDS);
            entry.get(10, TimeUnit.SECONDS);

            LotState state = persistedLotState(lot.getId());
            BigDecimal expectedAvailable = saleSucceeded ? new BigDecimal("3.000") : new BigDecimal("15.000");
            assertAll(
                    () -> assertEquals(0, new BigDecimal("15.000").compareTo(state.totalReceived())),
                    () -> assertEquals(0, expectedAvailable.compareTo(state.available())),
                    () -> assertTrue(state.available().compareTo(BigDecimal.ZERO) >= 0),
                    () -> assertEquals(2, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                    () -> assertEquals(2, movementItemCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                    () -> assertEquals(saleSucceeded ? 1 : 0,
                            movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode())),
                    () -> assertEquals(saleSucceeded ? 1 : 0,
                            movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode())),
                    () -> assertEquals(0, new BigDecimal("15.000").compareTo(
                            movementItemQuantitySumForLotAndType(lot.getId(), MovementType.ENTRY.getCode()))),
                    () -> assertEquals(0, (saleSucceeded ? new BigDecimal("12.000") : BigDecimal.ZERO).compareTo(
                            movementItemQuantitySumForLotAndType(lot.getId(), MovementType.EXIT.getCode()))));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void lossUpdatesPersistedStockAndRecordsLossMovement() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);

        service.registerLoss(lot.getId(), new BigDecimal("2.000"), "Avaria em transporte");

        LotState state = persistedLotState(lot.getId());
        assertAll(
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(state.totalReceived())),
                () -> assertEquals(0, new BigDecimal("8.000").compareTo(state.available())),
                () -> assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.LOSS.getCode())),
                () -> assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.LOSS.getCode())),
                () -> assertEquals(0, new BigDecimal("2.000").compareTo(
                        movementItemQuantitySumForLotAndType(lot.getId(), MovementType.LOSS.getCode()))),
                () -> assertEquals("Avaria em transporte", movementDescriptionForLotAndType(lot.getId(), MovementType.LOSS.getCode())));
    }

    @Test void lossRollsBackStockWhenMovementPersistenceFails() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        StockMovementService failingService = new StockMovementService(transactionManager,
                (connection, movement) -> {
                    new JdbcStockMovementRepository().save(connection, movement);
                    throw new IllegalStateException("forced loss movement failure");
                }, lotRepository);

        assertThrows(IllegalStateException.class,
                () -> failingService.registerLoss(lot.getId(), new BigDecimal("2.000"), "Avaria em transporte"));

        LotState state = persistedLotState(lot.getId());
        assertAll(
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(state.totalReceived())),
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(state.available())),
                () -> assertEquals(0, movementCountForLotAndType(lot.getId(), MovementType.LOSS.getCode())),
                () -> assertEquals(0, movementItemCountForLotAndType(lot.getId(), MovementType.LOSS.getCode())));
    }

    @Test void rejectsLossAboveAvailableBalanceWithoutRecordingMovement() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("3.000"));
        StockMovementService service = new StockMovementService(transactionManager, movementRepository, lotRepository);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerLoss(lot.getId(), new BigDecimal("4.000"), "Avaria em transporte"));

        assertEquals(0, new BigDecimal("3.000").compareTo(persistedLotState(lot.getId()).available()));
        assertEquals(0, movementCountForLotAndType(lot.getId(), MovementType.LOSS.getCode()));
        assertEquals(0, movementItemCountForLotAndType(lot.getId(), MovementType.LOSS.getCode()));
    }

    @Test void rollsBackMovementHeaderAndEarlierItemsWhenALaterItemInsertFails() {
        ReadOnlyLot lot = createInitialLot(new BigDecimal("10.000"));
        UUID movementId = UUID.randomUUID();
        StockMovement movement = StockMovement.reconstitute(movementId, MovementType.EXIT, LocalDateTime.now(),
                "Movement with invalid second item", List.of(
                        StockMovementItem.create(lot.getId(), BigDecimal.ONE),
                        StockMovementItem.create(UUID.randomUUID(), BigDecimal.ONE)));

        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> transactionManager.execute(connection -> movementRepository.save(connection, movement)));

        assertAll(
                () -> assertInstanceOf(SQLException.class, failure.getCause(),
                        "PersistenceException should preserve the original JDBC cause"),
                () -> assertEquals(0, countById("SELECT COUNT(*) FROM stock_movements WHERE id = ?", movementId),
                        "The movement header must roll back when an item violates its lot foreign key"),
                () -> assertEquals(0, countById("SELECT COUNT(*) FROM stock_movement_items WHERE stock_movement_id = ?", movementId),
                        "Items written before the failing item must roll back too"),
                () -> assertEquals(0, new BigDecimal("10.000").compareTo(persistedLotState(lot.getId()).available()),
                        "A history-only failure must not affect lot stock"));
    }

    private boolean attemptSale(StockMovementService service, UUID lotId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        return attemptSale(service, lotId, new BigDecimal("7.000"), ready, start);
    }

    private boolean attemptSale(StockMovementService service, UUID lotId, BigDecimal quantity,
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Sales did not receive start signal");
        try {
            service.registerSale(lotId, quantity);
            return true;
        } catch (IllegalArgumentException expectedInsufficientStock) {
            return false;
        }
    }

    private void attemptAdditionalEntry(StockMovementService service, UUID lotId, BigDecimal quantity,
            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Entries did not receive start signal");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Entry thread was interrupted before start", e);
        }
        service.registerAdditionalEntry(lotId, quantity);
    }

    @Test void rejectsUnknownProductAndNullIds() {
        assertThrows(IllegalArgumentException.class,
                () -> lots.save(null, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> lots.findByProductId(null));
        assertThrows(IllegalArgumentException.class, () -> lots.getTotalAvailableQuantity(null));
        assertThrows(IllegalArgumentException.class, () -> lots.findById(UUID.randomUUID()));
    }

    @Test void findsAllProductLotsAndTotalsOnlyNonExpiredStock() {
        LocalDate today = LocalDate.now();
        lots.save(product.getId(), today, today.plusDays(1), new BigDecimal("5.000"));
        lots.save(product.getId(), today, today.plusDays(2), new BigDecimal("7.000"));
        lots.save(product.getId(), today.minusDays(3), today.minusDays(1), new BigDecimal("11.000"));

        assertEquals(3, lots.findByProductId(product.getId()).size(),
                "The product query should include all its lots, including expired lots");
        assertEquals(0, new BigDecimal("12.000").compareTo(lots.getTotalAvailableQuantity(product.getId())),
                "The stock aggregate should exclude expired lots");
    }

    private int movementCountForLotAndType(UUID lotId, int movementType) {
        return count("SELECT COUNT(DISTINCT sm.id) FROM stock_movements sm "
                + "JOIN stock_movement_items smi ON smi.stock_movement_id = sm.id "
                + "WHERE smi.lot_id = ? AND sm.type = ?", lotId, movementType);
    }

    private int movementItemCountForLotAndType(UUID lotId, int movementType) {
        return count("SELECT COUNT(*) FROM stock_movement_items smi "
                + "JOIN stock_movements sm ON sm.id = smi.stock_movement_id "
                + "WHERE smi.lot_id = ? AND sm.type = ?", lotId, movementType);
    }

    private BigDecimal movementItemQuantityForLotAndType(UUID lotId, int movementType) {
        String sql = "SELECT smi.quantity FROM stock_movement_items smi "
                + "JOIN stock_movements sm ON sm.id = smi.stock_movement_id "
                + "WHERE smi.lot_id = ? AND sm.type = ?";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId.toString());
            statement.setInt(2, movementType);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("Entry movement item not found in integration database");
                return result.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read persisted movement item quantity", e);
        }
    }

    private BigDecimal movementItemQuantitySumForLotAndType(UUID lotId, int movementType) {
        String sql = "SELECT COALESCE(SUM(smi.quantity), 0) FROM stock_movement_items smi "
                + "JOIN stock_movements sm ON sm.id = smi.stock_movement_id "
                + "WHERE smi.lot_id = ? AND sm.type = ?";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId.toString());
            statement.setInt(2, movementType);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("Could not aggregate movement item quantities");
                return result.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not aggregate persisted movement item quantities", e);
        }
    }

    private String movementDescriptionForLotAndType(UUID lotId, int movementType) {
        String sql = "SELECT sm.description FROM stock_movements sm "
                + "JOIN stock_movement_items smi ON smi.stock_movement_id = sm.id "
                + "WHERE smi.lot_id = ? AND sm.type = ?";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId.toString());
            statement.setInt(2, movementType);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("Movement not found in integration database");
                return result.getString(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read persisted movement description", e);
        }
    }

    private LotState persistedLotState(UUID lotId) {
        String sql = "SELECT total_received_quantity, available_quantity FROM lots WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("Lot not found in integration database");
                return new LotState(result.getBigDecimal("total_received_quantity"), result.getBigDecimal("available_quantity"));
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read persisted lot quantities", e);
        }
    }

    private BigDecimal persistedQuantity(UUID lotId) {
        String sql = "SELECT available_quantity FROM lots WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AssertionError("Lot not found in integration database");
                return result.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not read persisted lot quantity", e);
        }
    }

    private Lot insertLotDirectly(BigDecimal quantity) {
        Lot lot = Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(20), quantity);
        transactionManager.execute(connection -> lotRepository.save(connection, lot));
        return lot;
    }

    private ReadOnlyLot createInitialLot(BigDecimal quantity) {
        LocalDate today = LocalDate.now();
        return lots.save(product.getId(), today, today.plusDays(20), quantity);
    }

    private int count(String sql, UUID lotId, int movementType) {
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId.toString());
            statement.setInt(2, movementType);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not inspect integration test data", e);
        }
    }

    private int countById(String sql, UUID id) {
        try (Connection connection = DatabaseConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not inspect integration test data", e);
        }
    }

    private record LotState(BigDecimal totalReceived, BigDecimal available) {}
}
