package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.exception.PersistenceException;
import marketcontrol.http.server.infrastructure.database.DatabaseConnection;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.domain.model.Product;
import marketcontrol.service.CategoryService;
import marketcontrol.service.ProductService;

class JdbcProductRepositoryIntegrationTest {
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private JdbcProductRepository repository;
    private ReadOnlyCategory category;
    private ReadOnlyProduct product;
    private List<UUID> preexistingProductIds;

    @BeforeEach void setUp() {
        repository = new JdbcProductRepository();
        preexistingProductIds = repository.findAll().stream().map(item -> item.getId()).toList();
        CategoryService categories = new CategoryService(new JdbcCategoryRepository());
        ProductService products = new ProductService(repository, categories);
        category = categories.saveCategory("Jdbc product " + run, "Category description", true);
        product = products.saveProduct("Mapped product", run, "Description", category.getId(), UnitOfMeasure.KILOGRAM);
    }

    @AfterEach void cleanUp() {
        if (category != null) DatabaseTestCleanup.deleteCategory(category.getId());
    }

    @Test void mapsProductWithCallerConnectionWithoutClosingIt() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            var found = repository.findById(connection, product.getId());

            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(product.getId(), found.orElseThrow().getId()),
                    () -> assertEquals("Mapped product", found.orElseThrow().getName()),
                    () -> assertEquals("Description", found.orElseThrow().getDescription()),
                    () -> assertEquals(UnitOfMeasure.KILOGRAM, found.orElseThrow().getUnit()),
                    () -> assertEquals(category.getId(), found.orElseThrow().getCategory().getId()),
                    () -> assertFalse(connection.isClosed(), "Repository must leave caller-owned connection open"));

            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT 1")) {
                assertTrue(result.next(), "Caller should still be able to use the connection");
            }
        }
    }

    @Test void findByIdWithCallerConnectionReturnsEmptyWhenProductDoesNotExist() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertTrue(repository.findById(connection, UUID.randomUUID()).isEmpty());
            assertFalse(connection.isClosed(), "Repository must not close a caller-owned connection");
        }
    }

    @Test void returnsEmptyForUnknownProductId() {
        assertTrue(repository.findProductById(UUID.randomUUID()).isEmpty());
    }

    @Test void findAllLeavesPreexistingProductsAfterTheTestFixtureIsRemoved() {
        DatabaseTestCleanup.deleteCategory(category.getId());
        category = null;

        assertEquals(preexistingProductIds, repository.findAll().stream().map(item -> item.getId()).toList());
    }

    @Test void findsProductWithRepositoryManagedConnectionAndMapsRelatedValues() {
        var found = repository.findProductById(product.getId());

        assertTrue(found.isPresent());
        assertAll(
                () -> assertEquals(product.getId(), found.orElseThrow().getId()),
                () -> assertEquals("Mapped product", found.orElseThrow().getName()),
                () -> assertEquals("Description", found.orElseThrow().getDescription()),
                () -> assertEquals(UnitOfMeasure.KILOGRAM, found.orElseThrow().getUnit()),
                () -> assertEquals(category.getId(), found.orElseThrow().getCategory().getId()));
    }

    @Test void rejectsNullConnectionAndIdForConnectionAwareLookup() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> repository.findById(null, UUID.randomUUID()));
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertThrows(IllegalArgumentException.class, () -> repository.findById(connection, null));
        }
    }

    @Test void findAllReturnsMultipleProductsWithMappedCategoryAndUnit() {
        Product second = Product.create("Second mapped product", run + "-SECOND", null,
                product.getCategory(), UnitOfMeasure.GRAM);
        repository.save(second);

        var found = repository.findAll().stream()
                .filter(item -> item.getId().equals(product.getId()) || item.getId().equals(second.getId()))
                .toList();

        assertEquals(2, found.size());
        assertAll(
                () -> assertEquals("Mapped product", found.stream().filter(item -> item.getId().equals(product.getId()))
                        .findFirst().orElseThrow().getName()),
                () -> assertEquals(UnitOfMeasure.KILOGRAM, found.stream().filter(item -> item.getId().equals(product.getId()))
                        .findFirst().orElseThrow().getUnit()),
                () -> assertEquals("Second mapped product", found.stream().filter(item -> item.getId().equals(second.getId()))
                        .findFirst().orElseThrow().getName()),
                () -> assertEquals(UnitOfMeasure.GRAM, found.stream().filter(item -> item.getId().equals(second.getId()))
                        .findFirst().orElseThrow().getUnit()),
                () -> assertEquals(category.getId(), found.getFirst().getCategory().getId()));
    }

    @Test void findByBarCodeFindsProductAndNormalizesInput() {
        var found = repository.findByBarCode("  " + run.toLowerCase() + "  ");

        assertTrue(found.isPresent());
        assertEquals(product.getId(), found.orElseThrow().getId());
        assertEquals(UnitOfMeasure.KILOGRAM, found.orElseThrow().getUnit());
        assertEquals(category.getId(), found.orElseThrow().getCategory().getId());
    }

    @Test void findByBarCodeReturnsEmptyWhenProductDoesNotExist() {
        assertTrue(repository.findByBarCode("UNKNOWN-" + run).isEmpty());
    }

    @Test void existsByBarCodeFindsProductAndNormalizesInput() {
        assertTrue(repository.existsByBarCode("  " + run.toLowerCase() + "  "));
        assertFalse(repository.existsByBarCode("UNKNOWN-" + run));
    }

    @Test void existsByBarCodeAndIdNotExcludesCurrentProductAndFindsAnotherProductForDifferentId() {
        assertFalse(repository.existsByBarCodeAndIdNot("  " + run.toLowerCase() + "  ", product.getId()));
        assertTrue(repository.existsByBarCodeAndIdNot(run, UUID.randomUUID()));
    }

    @Test void savesProductAndReadsBackPersistedCategoryAndUnit() {
        Product toSave = Product.create("Saved through repository", run + "-SAVE", "Save description",
                product.getCategory(), UnitOfMeasure.LITER);

        repository.save(toSave);

        var persisted = repository.findProductById(toSave.getId()).orElseThrow();
        assertAll(
                () -> assertEquals("Saved through repository", persisted.getName()),
                () -> assertEquals((run + "-SAVE").toUpperCase(), persisted.getBarCode()),
                () -> assertEquals("Save description", persisted.getDescription()),
                () -> assertEquals(category.getId(), persisted.getCategory().getId()),
                () -> assertEquals(UnitOfMeasure.LITER, persisted.getUnit()));
    }

    @Test void translatesUniqueBarcodeViolationAndPreservesSqlCause() {
        Product duplicateBarcode = Product.create("Duplicate barcode", run, null,
                product.getCategory(), UnitOfMeasure.UNIT);

        PersistenceException failure = assertThrows(PersistenceException.class, () -> repository.save(duplicateBarcode));

        assertInstanceOf(SQLException.class, failure.getCause());
    }

    @Test void updatePersistsChangedProductFields() {
        Product editable = Product.reconstitute(product.getId(), product.getName(), product.getBarCode(),
                product.getDescription(), product.getCategory(), product.getUnit());
        editable.edit("Updated mapped product", run + "-UPDATED", "Updated description", product.getCategory());

        repository.update(editable);

        var persisted = repository.findProductById(product.getId()).orElseThrow();
        assertAll(
                () -> assertEquals("Updated mapped product", persisted.getName()),
                () -> assertEquals((run + "-UPDATED").toUpperCase(), persisted.getBarCode()),
                () -> assertEquals("Updated description", persisted.getDescription()),
                () -> assertEquals(category.getId(), persisted.getCategory().getId()),
                () -> assertEquals(UnitOfMeasure.KILOGRAM, persisted.getUnit()));
    }

    @Test void updateOfUnknownProductDoesNotCreateOrThrow() {
        Product missing = Product.reconstitute(UUID.randomUUID(), "Missing product", run + "-MISSING", null,
                product.getCategory(), UnitOfMeasure.UNIT);

        assertDoesNotThrow(() -> repository.update(missing));
        assertTrue(repository.findProductById(missing.getId()).isEmpty());
    }

    @Test void rejectsInvalidArgumentsForPublicMethods() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findProductById(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findById(null, product.getId())),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findById(connection, null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findByBarCode(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.findByBarCode("")),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.existsByBarCode(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.existsByBarCode("")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> repository.existsByBarCodeAndIdNot(null, product.getId())),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.existsByBarCodeAndIdNot(run, null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.save(null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> repository.update(null)));
        }
    }

    @Test void schemaRejectsNullUnitAndCategoryActiveBeforeRepositoryMapping() throws Exception {
        assertThrows(SQLException.class,
                () -> update("UPDATE products SET unit = NULL WHERE id = ?", product.getId().toString()));
        assertThrows(SQLException.class,
                () -> update("UPDATE categories SET active = NULL WHERE id = ?", category.getId().toString()));

        assertTrue(repository.findProductById(product.getId()).isPresent(),
                "Rejected updates must leave the valid persisted row intact");
    }

    private static void update(String sql, Object... values) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

}
