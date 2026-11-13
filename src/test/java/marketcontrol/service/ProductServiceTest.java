package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import marketcontrol.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.domain.model.*;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.*;
import marketcontrol.domain.repository.*;
import marketcontrol.dtos.ProductDTO;
import marketcontrol.DatabaseTestCleanup;

class ProductServiceTest {
    private ProductService service;
    private CategoryService categories;
    private ReadOnlyCategory category;
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach void setUp() {
        categories = new CategoryService(new JdbcCategoryRepository());
        service = new ProductService(new JdbcProductRepository(), categories);
        category = categories.saveCategory("Feijão " + run, "Alimentação", true);
    }
    @AfterEach void cleanUp() { if (category != null) DatabaseTestCleanup.deleteCategory(category.getId()); }
    private ReadOnlyProduct add(String name, String code) {
        return service.saveProduct(name, run + code, null, category.getId(), UnitOfMeasure.UNIT);
    }
    private ProductDTO update(String name, String code) {
        return new ProductDTO(name, code.isBlank() ? code : run + code, null, category.getId(), UnitOfMeasure.UNIT);
    }

    @Test void savesAndListsProducts() {
        add("Iphone 17", "111"); add("Iphone 18", "222");
        List<ReadOnlyProduct> ownProducts = service.getProducts().stream()
                .filter(p -> p.getCategory().getId().equals(category.getId())).toList();
        assertEquals(Set.of("Iphone 17", "Iphone 18"), ownProducts.stream().map(ReadOnlyProduct::getName).collect(java.util.stream.Collectors.toSet()));
        assertThrows(UnsupportedOperationException.class, () -> ownProducts.clear());
    }
    @Test void rejectsDuplicateBarcodeIgnoringCaseAndSpaces() {
        add("Iphone 17", "abc");
        assertThrows(IllegalArgumentException.class, () -> service.saveProduct(
                "Iphone 18", " " + run.toUpperCase() + "ABC ", null, category.getId(), UnitOfMeasure.UNIT));
    }
    @Test void findsBarcodeIgnoringCaseAndSpaces() {
        add("Iphone 17", "abc");
        assertEquals("Iphone 17", service.findByBarCode(" " + run + "ABC ").getName());
        assertTrue(service.existsBarCode(" " + run + "abc "));
        assertThrows(IllegalArgumentException.class, () -> service.findByBarCode(run + "missing"));
        assertThrows(IllegalArgumentException.class, () -> service.existsBarCode(" "));
    }
    @Test void editsProductAndUpdatesBarcodeLookup() {
        ReadOnlyProduct original = add("Iphone 17", "111");
        ReadOnlyProduct edited = service.editProduct(original.getId(), update("Xiaomi 10", "222"));
        assertEquals(original.getId(), edited.getId());
        assertEquals("Xiaomi 10", service.findByBarCode(run + "222").getName());
        assertThrows(IllegalArgumentException.class, () -> service.findByBarCode(run + "111"));
    }
    @Test void rejectsDuplicateBarcodeOnEditWithoutChangingOriginal() {
        ReadOnlyProduct first = add("Iphone 17", "111"); add("Iphone 18", "222");
        assertThrows(IllegalArgumentException.class, () -> service.editProduct(first.getId(), update("Changed", "222")));
        assertEquals("Iphone 17", service.findByBarCode(run + "111").getName());
    }
    @Test void rejectsInvalidEditRequests() {
        ReadOnlyProduct product = add("Iphone 17", "111");
        assertThrows(IllegalArgumentException.class, () -> service.editProduct(product.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> service.editProduct(UUID.randomUUID(), update("New", "222")));
        assertThrows(IllegalArgumentException.class, () -> service.editProduct(product.getId(), update("New", " ")));
    }

}
