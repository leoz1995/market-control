package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.infrastructure.database.DatabaseConnection;
import marketcontrol.service.CategoryService;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;
import marketcontrol.service.StockMovementService;
import marketcontrol.domain.repository.LotRepository;

class JdbcLotRepositoryIntegrationTest {
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private ReadOnlyCategory category;
    private ReadOnlyProduct product;
    private LotRepository repository;
    private LotService lots;
    private StockMovementService movements;

    @BeforeEach void setUp() {
        CategoryService categories = new CategoryService(new JdbcCategoryRepository());
        ProductService products = new ProductService(new JdbcProductRepository(), categories);
        category = categories.saveCategory("Jdbc lot " + run, null, true);
        product = products.saveProduct("Lot lookup product", run, null, category.getId(), UnitOfMeasure.UNIT);
        repository = new JdbcLotRepository(new JdbcProductRepository());
        var transactionManager = new JdbcTransactionManager();
        var movementRepository = new JdbcStockMovementRepository();
        movements = new StockMovementService(transactionManager, movementRepository, repository);
        lots = new LotService(products, repository, transactionManager, movements);
    }

    @AfterEach void cleanUp() {
        if (category != null) DatabaseTestCleanup.deleteCategory(category.getId());
    }

    @Test void mapsExhaustedLotWithCallerConnectionAndLeavesItOpen() throws Exception {
        LocalDate today = LocalDate.now();
        ReadOnlyLot created = lots.save(product.getId(), today, today.plusDays(5), new BigDecimal("6.000"));
        movements.registerSale(created.getId(), new BigDecimal("6.000"));

        try (Connection connection = DatabaseConnection.getConnection()) {
            var found = repository.findById(connection, created.getId());

            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(0, new BigDecimal("6.000").compareTo(found.orElseThrow().getTotalReceivedQuantity())),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(found.orElseThrow().getAvailableQuantity()),
                            "An exhausted lot remains a valid persisted lot"),
                    () -> assertFalse(connection.isClosed(), "Repository must leave caller-owned connection open"));
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next(), "Caller should still be able to use the connection");
            }
        }
    }

    @Test void returnsEmptyForUnknownLotIdUsingCallerConnection() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertTrue(repository.findById(connection, UUID.randomUUID()).isEmpty());
            assertFalse(connection.isClosed());
        }
    }

    @Test void findsLotWithRepositoryManagedConnectionAndFindsLotsByProduct() {
        ReadOnlyLot created = lots.save(product.getId(), LocalDate.now(), LocalDate.now().plusDays(5), new BigDecimal("4.250"));

        var found = repository.findById(created.getId());
        var productLots = repository.findByProductId(product.getId());

        assertTrue(found.isPresent());
        assertAll(
                () -> assertEquals(0, new BigDecimal("4.250").compareTo(found.orElseThrow().getTotalReceivedQuantity())),
                () -> assertEquals(0, new BigDecimal("4.250").compareTo(found.orElseThrow().getAvailableQuantity())),
                () -> assertTrue(productLots.stream().anyMatch(lot -> lot.getId().equals(created.getId()))));
    }

    @Test void persistedLotsRespectQuantityChecksAndHaveNoInvalidRows() throws Exception {
        String checks = "SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN "
                + "('chk_lots_total_received_positive', 'chk_lots_available_nonnegative', "
                + "'chk_lots_available_not_greater_than_received')";
        String invalidRows = "SELECT COUNT(*) FROM lots WHERE total_received_quantity <= 0 "
                + "OR available_quantity < 0 OR available_quantity > total_received_quantity";

        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement checkStatement = connection.prepareStatement(checks);
                ResultSet checkResult = checkStatement.executeQuery()) {
            assertTrue(checkResult.next());
            assertEquals(3, checkResult.getInt(1), "Configured MariaDB schema should expose all lot quantity checks");
        }
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement invalidStatement = connection.prepareStatement(invalidRows);
                ResultSet invalidResult = invalidStatement.executeQuery()) {
            assertTrue(invalidResult.next());
            assertEquals(0, invalidResult.getInt(1), "Do not alter data; report any invalid persisted lot");
        }
    }

    @Test void rejectsNullConnectionAndIdForConnectionAwareLookup() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> repository.findById(null, UUID.randomUUID()));
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertThrows(IllegalArgumentException.class, () -> repository.findById(connection, null));
        }
    }
}
