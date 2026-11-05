package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.service.CategoryService;

class ProductTest {
	private CategoryService categoryService = new CategoryService();
	private ReadOnlyCategory cat = categoryService.saveCategory("Feijão", "Enlatados", true);
	Category category = categoryService.findById(cat.getId());
	
    @Test
    void shouldCreateProductWithValidData() {
        Product product = Product.create(
            "Iphone 17",
            "A1264897S302027",
            "Pro Max Edition",
            true, category
        );

        assertNotNull(product);
        assertNotNull(product.getId());
        assertEquals("Iphone 17", product.getName());
        assertEquals("A1264897S302027", product.getBarCode());
        assertEquals("Pro Max Edition", product.getDescription());
        assertTrue(product.isActive());
    }

    @Test
    void shouldGenerateUniqueIds() {
        Product first = Product.create("Iphone 17", "111", null, true, category);
        Product second = Product.create("Iphone 18", "222", null, true, category);

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void shouldRejectProductWithNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create(null, "111", null, true, category));
    }

    @Test
    void shouldRejectProductWithBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create("   ", "111", null, true, category));
    }

    @Test
    void shouldNormalizeProductName() {
        Product product = Product.create(
            "  Iphone 17  ", "111", null, true, category
        );

        assertEquals("Iphone 17", product.getName());
    }

    @Test
    void shouldRejectProductWithNullBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create("Iphone 17", null, null, true, category));
    }

    @Test
    void shouldRejectProductWithBlankBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create("Iphone 17", "   ", null, true, category));
    }

    @Test
    void shouldNormalizeBarcode() {
        Product product = Product.create(
            "Iphone 17", "  111222333  ", null, true, category
        );

        assertEquals("111222333", product.getBarCode());
    }

    @Test
    void shouldAllowNullDescription() {
        Product product = Product.create("Iphone 17", "111", null, true, category);

        assertNull(product.getDescription());
    }

    @Test
    void shouldConvertBlankDescriptionToNull() {
        Product product = Product.create("Iphone 17", "111", "   ", true, category);

        assertNull(product.getDescription());
    }

    @Test
    void shouldNormalizeDescription() {
        Product product = Product.create(
            "Iphone 17", "111", "  Pro Max  ", true, category
        );

        assertEquals("Pro Max", product.getDescription());
    }

    @Test
    void shouldAllowInactiveProduct() {
        Product product = Product.create("Iphone 17", "111", null, false, category);

        assertFalse(product.isActive());
    }

    @Test
    void shouldEditProductWithValidData() {
        Product product = Product.create("Iphone 17", "111", "Pro", true, category);

        product.edit("Xiaomi 10", "222", "New Generation", false, category);

        assertEquals("Xiaomi 10", product.getName());
        assertEquals("222", product.getBarCode());
        assertEquals("New Generation", product.getDescription());
        assertFalse(product.isActive());
    }

    @Test
    void shouldPreserveIdAfterEditing() {
        Product product = Product.create("Iphone 17", "111", null, true, category);
        UUID originalId = product.getId();

        product.edit("Xiaomi 10", "222", null, false, category);

        assertEquals(originalId, product.getId());
    }

    @Test
    void shouldNormalizeDataWhenEditing() {
        Product product = Product.create("Iphone 17", "111", null, true, category);

        product.edit("  Xiaomi 10  ", "  222  ", "  New  ", true, category);

        assertEquals("Xiaomi 10", product.getName());
        assertEquals("222", product.getBarCode());
        assertEquals("New", product.getDescription());
    }

    @Test
    void shouldRejectEditWithNullName() {
        Product product = Product.create("Iphone 17", "111", null, true, category);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit(null, "222", null, true, category));
    }

    @Test
    void shouldRejectEditWithBlankName() {
        Product product = Product.create("Iphone 17", "111", null, true, category);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("   ", "222", null, true, category));
    }

    @Test
    void shouldRejectEditWithNullBarcode() {
        Product product = Product.create("Iphone 17", "111", null, true, category);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("Xiaomi 10", null, null, true, category));
    }

    @Test
    void shouldRejectEditWithBlankBarcode() {
        Product product = Product.create("Iphone 17", "111", null, true, category);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("Xiaomi 10", "   ", null, true, category));
    }

    @Test
    void shouldPreserveDataWhenEditFails() {
        Product product = Product.create("Iphone 17", "111", "Pro", true, category);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("   ", "222", "New", false, category));

        assertEquals("Iphone 17", product.getName());
        assertEquals("111", product.getBarCode());
        assertEquals("Pro", product.getDescription());
        assertTrue(product.isActive());
    }
}
