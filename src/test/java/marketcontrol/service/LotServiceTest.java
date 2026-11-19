package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.repository.CategoryRepository;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcTransactionManager;
import marketcontrol.service.CategoryService;
import marketcontrol.service.ProductService;
import marketcontrol.service.SupplierService;
import marketcontrol.transaction.TransactionAction;
import marketcontrol.transaction.TransactionManager;
import marketcontrol.transaction.TransactionWork;

class LotServiceTest {
    @Test void eachAcquisitionCreatesANewLotAndInitialEntryWithHistoricalCost() {
        Category category = Category.create("Food", "Food category", true);
        Product product = Product.create("Rice", "123", null, category, UnitOfMeasure.UNIT);
        Supplier supplier = Supplier.create("Legal", "Trade", "12345678901234", "a@b", "1", "A");
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a)->null);
        List<marketcontrol.domain.model.Lot> savedLots = new ArrayList<>();
        List<BigDecimal> savedCosts = new ArrayList<>();
        CategoryRepository categories = proxy(CategoryRepository.class, (method,args)-> method.equals("findById") ? Optional.of(category) : defaultValue(method));
        ProductRepository products = proxy(ProductRepository.class, (method,args)-> method.equals("findProductById") ? Optional.of(product) : defaultValue(method));
        SupplierRepository suppliers = proxy(SupplierRepository.class, (method,args)-> method.equals("findById") ? Optional.of(supplier) : defaultValue(method));
        LotRepository lots = proxy(LotRepository.class, (method,args)-> {
            if (method.equals("saveWithInitialEntry")) {
                savedLots.add((marketcontrol.domain.model.Lot)args[1]);
                savedCosts.add((BigDecimal)args[3]);
            }
            return defaultValue(method);
        });
        TransactionManager tx = new TransactionManager() {
            public void execute(TransactionWork work) { work.execute(connection); }
            public <T> T executeAndReturn(TransactionAction<T> work) { return work.execute(connection); }
        };
        ProductService productService = new ProductService(products, new CategoryService(categories));
        SupplierService supplierService = new SupplierService(suppliers);
        LotService service = new LotService(productService, lots, supplierService, tx);

        var first = service.save(product.getId(), supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("10"), new BigDecimal("2.35"));
        var second = service.save(product.getId(), supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(40), new BigDecimal("5"), new BigDecimal("2.80"));

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, savedLots.size());
        assertEquals(List.of(new BigDecimal("2.35"), new BigDecimal("2.80")), savedCosts);
    }

    private interface Handler { Object apply(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a)->handler.apply(m.getName(), a));
    }
    private static Object defaultValue(String method) {
        return switch (method) {
            case "existsByName", "existsById", "existsByNameAndIdNot", "existsByBarCode", "existsByBarCodeAndIdNot", "existsByDocument" -> false;
            case "findAll", "findByProductId" -> List.of();
            case "getTotalAvailableQuantity" -> BigDecimal.ZERO;
            case "findById", "findProductById", "findByBarCode" -> Optional.empty();
            default -> null;
        };
    }
}
