package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.infrastructure.database.DatabaseConnection;
import marketcontrol.service.CategoryService;
import marketcontrol.service.ProductService;

class JdbcProductRepositoryIntegrationTest {
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private JdbcProductRepository repository;
    private ReadOnlyCategory category;
    private ReadOnlyProduct product;

    @BeforeEach void setUp() {
        repository = new JdbcProductRepository();
        CategoryService categories = new CategoryService(new JdbcCategoryRepository());
        ProductService products = new ProductService(repository, categories);
        category = categories.saveCategory("Jdbc product " + run, null, true);
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

    @Test void returnsEmptyForUnknownProductId() {
        assertTrue(repository.findProductById(UUID.randomUUID()).isEmpty());
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
}
