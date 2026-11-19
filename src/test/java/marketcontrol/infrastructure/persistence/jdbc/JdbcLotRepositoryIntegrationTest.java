package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.service.CategoryService;
import marketcontrol.service.ProductService;

class JdbcLotRepositoryIntegrationTest {
    private final String run = UUID.randomUUID().toString();
    private JdbcLotRepository lots;
    private ProductService products;
    private ReadOnlyProduct product;
    private UUID categoryId;

    @BeforeEach void setUp() {
        ProductRepository productRepository = new JdbcProductRepository();
        CategoryService categories = new CategoryService(new JdbcCategoryRepository());
        products = new ProductService(productRepository, categories);
        var category = categories.saveCategory("Lot test " + run, "Lot integration category", true);
        categoryId = category.getId();
        product = products.saveProduct("Lot product", "LOT-" + run, null, categoryId, UnitOfMeasure.KILOGRAM);
        lots = new JdbcLotRepository(productRepository);
    }

    @AfterEach void cleanUp() { DatabaseTestCleanup.deleteCategory(categoryId); }

    @Test void savesAndReadsLotUsingCurrentSchema() {
        Lot lot = Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(10), new BigDecimal("12.500"));
        new JdbcTransactionManager().execute(conn -> lots.save(conn, lot));
        var persisted = lots.findById(lot.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(lot.getId(), persisted.getId()),
                () -> assertEquals(product.getId(), persisted.getProduct().getId()),
                () -> assertEquals(new BigDecimal("12.500"), persisted.getAvailableQuantity()),
                () -> assertTrue(lots.findById(UUID.randomUUID()).isEmpty()));
    }

    @Test void aggregatesNonExpiredLotsAndConditionallyUpdatesBalances() {
        Lot current = Lot.create(product, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), new BigDecimal("10"));
        Lot expired = Lot.create(product, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), new BigDecimal("7"));
        JdbcTransactionManager tx = new JdbcTransactionManager();
        tx.execute(conn -> { lots.save(conn, current); lots.save(conn, expired); });

        assertEquals(new BigDecimal("10.000"), lots.getTotalAvailableQuantity(product.getId()));
        int saleRows = tx.executeAndReturn(conn -> lots.decreaseAvailableQuantityForSale(conn, current.getId(), new BigDecimal("4")));
        int insufficientRows = tx.executeAndReturn(conn -> lots.decreaseAvailableQuantityForSale(conn, current.getId(), new BigDecimal("7")));
        int expiredSaleRows = tx.executeAndReturn(conn -> lots.decreaseAvailableQuantityForSale(conn, expired.getId(), BigDecimal.ONE));
        int expiredLossRows = tx.executeAndReturn(conn -> lots.decreaseAvailableQuantityForLoss(conn, expired.getId(), new BigDecimal("2")));
        assertEquals(1, saleRows);
        assertEquals(0, insufficientRows);
        assertEquals(0, expiredSaleRows);
        assertEquals(1, expiredLossRows);
        assertEquals(new BigDecimal("6.000"), lots.findById(current.getId()).orElseThrow().getAvailableQuantity());
        assertEquals(new BigDecimal("5.000"), lots.findById(expired.getId()).orElseThrow().getAvailableQuantity());
        assertEquals(2, lots.findByProductId(product.getId()).size());
    }

    @Test void rejectsSaleWhenBalanceWouldBecomeNegative() {
        Lot lot = Lot.create(product, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE);
        new JdbcTransactionManager().execute(conn -> lots.save(conn, lot));
        int affected = new JdbcTransactionManager().executeAndReturn(conn -> lots.decreaseAvailableQuantityForSale(conn, lot.getId(), new BigDecimal("1.001")));
        assertEquals(0, affected);
        assertEquals(0, BigDecimal.ONE.compareTo(lots.findById(lot.getId()).orElseThrow().getAvailableQuantity()));
    }
}
