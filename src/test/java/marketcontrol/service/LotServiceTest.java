package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.Lot;
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
import marketcontrol.transaction.TransactionManager;

class LotServiceTest {
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
                () -> assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                () -> assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.ENTRY.getCode())),
                () -> assertThrows(IllegalArgumentException.class, () -> lots.findById(lot.getId()),
                        "A persisted available quantity above the initial quantity violates Lot.reconstitute"));
    }

    @Test void saleUpdatesPersistedStockAndWritesExitMovement() {
        Lot lot = insertLotDirectly(new BigDecimal("10.000"));

        new StockMovementService(transactionManager, movementRepository, lotRepository)
                .registerSale(lot.getId(), new BigDecimal("3.000"));

        assertEquals(0, new BigDecimal("7.000").compareTo(persistedQuantity(lot.getId())));
        assertEquals(1, movementCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
        assertEquals(1, movementItemCountForLotAndType(lot.getId(), MovementType.EXIT.getCode()));
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

    @Test void rejectsUnknownProductAndNullIds() {
        assertThrows(IllegalArgumentException.class,
                () -> lots.save(null, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> lots.findByProductId(null));
        assertThrows(IllegalArgumentException.class, () -> lots.getTotalAvailableQuantity(null));
        assertThrows(IllegalArgumentException.class, () -> lots.findById(UUID.randomUUID()));
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
}
