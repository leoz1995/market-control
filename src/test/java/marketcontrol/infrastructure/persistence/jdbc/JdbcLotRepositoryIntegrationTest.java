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
import marketcontrol.domain.model.Lot;
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
        category = categories.saveCategory("Jdbc lot " + run, "Integration test category", true);
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

    @Test void savesLotWithCallerConnectionAndLeavesConnectionOpen() throws Exception {
        LocalDate today = LocalDate.now();
        Lot lot = Lot.create(product, today, today.plusDays(5), new BigDecimal("8.500"));

        try (Connection connection = DatabaseConnection.getConnection()) {
            repository.save(connection, lot);
            assertFalse(connection.isClosed(), "save(Connection, Lot) must not close the caller connection");
            var persisted = repository.findById(connection, lot.getId()).orElseThrow();
            assertAll(
                    () -> assertEquals(product.getId(), persisted.getProduct().getId()),
                    () -> assertEquals(0, new BigDecimal("8.500").compareTo(persisted.getTotalReceivedQuantity())),
                    () -> assertEquals(0, new BigDecimal("8.500").compareTo(persisted.getAvailableQuantity())),
                    () -> assertFalse(connection.isClosed()));
        }
    }

    @Test void findsMultipleLotsAndReturnsEmptyListForProductWithoutLots() {
        ReadOnlyLot first = lots.save(product.getId(), LocalDate.now(), LocalDate.now().plusDays(3), new BigDecimal("2"));
        ReadOnlyLot second = lots.save(product.getId(), LocalDate.now(), LocalDate.now().plusDays(4), new BigDecimal("3"));
        ReadOnlyProduct productWithoutLots = new ProductService(new JdbcProductRepository(), new CategoryService(new JdbcCategoryRepository()))
                .saveProduct("No lot product", run + "-EMPTY", null, category.getId(), UnitOfMeasure.UNIT);

        var found = repository.findByProductId(product.getId());

        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(lot -> lot.getId().equals(first.getId())));
        assertTrue(found.stream().anyMatch(lot -> lot.getId().equals(second.getId())));
        assertTrue(repository.findByProductId(productWithoutLots.getId()).isEmpty(),
                "A persisted product with no lots should return an empty list");
    }

    @Test void rejectsUnknownProductWhenFindingLots() {
        assertThrows(IllegalArgumentException.class, () -> repository.findByProductId(UUID.randomUUID()));
    }

    @Test void sumsAvailableNonExpiredQuantityAndReturnsZeroWhenThereAreNoLots() {
        assertEquals(0, BigDecimal.ZERO.compareTo(repository.getTotalAvailableQuantity(product.getId())));
        ReadOnlyLot active = lots.save(product.getId(), LocalDate.now(), LocalDate.now().plusDays(1), new BigDecimal("5"));
        ReadOnlyLot expired = lots.save(product.getId(), LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), new BigDecimal("7"));
        movements.registerSale(active.getId(), new BigDecimal("2"));

        assertEquals(0, new BigDecimal("3").compareTo(repository.getTotalAvailableQuantity(product.getId())));
        movements.registerSale(active.getId(), new BigDecimal("3"));
        assertEquals(0, BigDecimal.ZERO.compareTo(repository.getTotalAvailableQuantity(product.getId())),
                "A zero balance contributes zero and expired stock is excluded");
        assertEquals(0, new BigDecimal("7").compareTo(repository.findById(expired.getId()).orElseThrow().getAvailableQuantity()));
    }

    @Test void updatesAdditionalEntrySaleAndLossQuantitiesInDatabase() throws Exception {
        ReadOnlyLot lot = lots.save(product.getId(), LocalDate.now(), LocalDate.now().plusDays(5), new BigDecimal("10"));

        try (Connection connection = DatabaseConnection.getConnection()) {
            assertEquals(1, repository.increaseAvailableQuantityForAdditionalEntry(connection, lot.getId(), new BigDecimal("4")));
            assertEquals(1, repository.decreaseAvailableQuantityForSale(connection, lot.getId(), new BigDecimal("3")));
            assertEquals(1, repository.decreaseAvailableQuantityForLoss(connection, lot.getId(), new BigDecimal("2")));
            var persisted = repository.findById(connection, lot.getId()).orElseThrow();
            assertAll(
                    () -> assertEquals(0, new BigDecimal("14").compareTo(persisted.getTotalReceivedQuantity())),
                    () -> assertEquals(0, new BigDecimal("9").compareTo(persisted.getAvailableQuantity())),
                    () -> assertFalse(connection.isClosed()));
        }
    }

    @Test void stockUpdatesReturnZeroForInsufficientQuantityExpiredOrMissingLot() throws Exception {
        ReadOnlyLot lot = lots.save(product.getId(), LocalDate.now(), LocalDate.now().plusDays(5), new BigDecimal("4"));
        ReadOnlyLot expired = lots.save(product.getId(), LocalDate.now().minusDays(3), LocalDate.now().minusDays(1), new BigDecimal("4"));

        try (Connection connection = DatabaseConnection.getConnection()) {
            assertEquals(0, repository.decreaseAvailableQuantityForSale(connection, lot.getId(), new BigDecimal("5")));
            assertEquals(0, repository.decreaseAvailableQuantityForSale(connection, expired.getId(), BigDecimal.ONE));
            assertEquals(0, repository.decreaseAvailableQuantityForLoss(connection, UUID.randomUUID(), BigDecimal.ONE));
            assertEquals(0, repository.increaseAvailableQuantityForAdditionalEntry(connection, UUID.randomUUID(), BigDecimal.ONE));
            assertEquals(0, new BigDecimal("4").compareTo(repository.findById(connection, lot.getId()).orElseThrow().getAvailableQuantity()));
        }
    }

    @Test void rejectsInvalidStockUpdateArguments() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.save(null, Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE))),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.save(connection, null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findById((UUID) null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findById(null, UUID.randomUUID())),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findByProductId(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.getTotalAvailableQuantity(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.decreaseAvailableQuantityForSale(null, UUID.randomUUID(), BigDecimal.ONE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.decreaseAvailableQuantityForLoss(connection, null, BigDecimal.ONE)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.increaseAvailableQuantityForAdditionalEntry(connection, UUID.randomUUID(), null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.decreaseAvailableQuantityForSale(connection, UUID.randomUUID(), BigDecimal.ZERO)));
        }
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
