package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcLotRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcProductRepository;

class LotServiceTest {
    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private CategoryService categories;
    private ProductService products;
    private LotService lots;
    private ReadOnlyCategory category;
    private ReadOnlyProduct product;

    @BeforeEach void setUp() {
        categories = new CategoryService(new JdbcCategoryRepository());
        var productRepository = new JdbcProductRepository();
        products = new ProductService(productRepository, categories);
        lots = new LotService(products, new JdbcLotRepository(productRepository));
        category = categories.saveCategory("Lot test " + run, null, true);
        product = products.saveProduct("Test product", run, null, category.getId(), UnitOfMeasure.UNIT);
    }
    @AfterEach void cleanUp() { if (category != null) DatabaseTestCleanup.deleteCategory(category.getId()); }

    @Test void savesFindsSellsAndTotalsLotStockThroughJdbc() {
        LocalDate today = LocalDate.now();
        ReadOnlyLot lot = lots.save(product.getId(), today, today.plusDays(20), new BigDecimal("10.000"));
        assertEquals(lot.getId(), lots.findById(lot.getId()).getId());
        assertEquals(1, lots.findByProductId(product.getId()).size());
        assertEquals(0, new BigDecimal("10.000").compareTo(lots.getTotalAvailableQuantity(product.getId())));

        lots.seller(lot.getId(), new BigDecimal("3.000"));
        assertEquals(0, new BigDecimal("7.000").compareTo(lots.findById(lot.getId()).getAvailableQuantity()));
        assertEquals(0, new BigDecimal("7.000").compareTo(lots.getTotalAvailableQuantity(product.getId())));
    }

    @Test void rejectsUnknownProductAndNullIds() {
        assertThrows(IllegalArgumentException.class, () -> lots.save(null, LocalDate.now(), LocalDate.now().plusDays(1), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> lots.findByProductId(null));
        assertThrows(IllegalArgumentException.class, () -> lots.getTotalAvailableQuantity(null));
        assertThrows(IllegalArgumentException.class, () -> lots.findById(UUID.randomUUID()));
    }
}
