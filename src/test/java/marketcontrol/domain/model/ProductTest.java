package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.enums.UnitOfMeasure;

class ProductTest {
	private Category category = Category.create("Feijão", "Enlatados", true);
	
    @Test
    void shouldCreateProductWithValidData() {
        Product product = Product.create(
            "Iphone 17",
            "A1264897S302027",
            "Pro Max Edition",
            category
        , UnitOfMeasure.UNIT);

        assertNotNull(product);
        assertNotNull(product.getId());
        assertEquals("Iphone 17", product.getName());
        assertEquals("A1264897S302027", product.getBarCode());
        assertEquals("Pro Max Edition", product.getDescription());
    }

    @Test
    void shouldGenerateUniqueIds() {
        Product first = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);
        Product second = Product.create("Iphone 18", "222", null, category, UnitOfMeasure.UNIT);

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void shouldRejectProductWithNullName() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create(null, "111", null, category, UnitOfMeasure.UNIT));
    }

    @Test
    void shouldRejectProductWithBlankName() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create("   ", "111", null, category, UnitOfMeasure.UNIT));
    }

    @Test
    void shouldNormalizeProductName() {
        Product product = Product.create(
            "  Iphone 17  ", "111", null, category
        , UnitOfMeasure.UNIT);

        assertEquals("Iphone 17", product.getName());
    }

    @Test
    void shouldRejectProductWithNullBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create("Iphone 17", null, null, category, UnitOfMeasure.UNIT));
    }

    @Test
    void shouldRejectProductWithBlankBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> Product.create("Iphone 17", "   ", null, category, UnitOfMeasure.UNIT));
    }

    @Test
    void shouldNormalizeBarcode() {
        Product product = Product.create(
            "Iphone 17", "  111222333  ", null, category
        , UnitOfMeasure.UNIT);

        assertEquals("111222333", product.getBarCode());
    }

    @Test
    void shouldAllowNullDescription() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);

        assertNull(product.getDescription());
    }

    @Test
    void shouldConvertBlankDescriptionToNull() {
        Product product = Product.create("Iphone 17", "111", "   ", category, UnitOfMeasure.UNIT);

        assertNull(product.getDescription());
    }

    @Test
    void shouldNormalizeDescription() {
        Product product = Product.create(
            "Iphone 17", "111", "  Pro Max  ", category
        , UnitOfMeasure.UNIT);

        assertEquals("Pro Max", product.getDescription());
    }
    
    @Test
    void shouldEditProductWithValidData() {
        Product product = Product.create("Iphone 17", "111", "Pro", category, UnitOfMeasure.UNIT);

        product.edit("Xiaomi 10", "222", "New Generation", category);

        assertEquals("Xiaomi 10", product.getName());
        assertEquals("222", product.getBarCode());
        assertEquals("New Generation", product.getDescription());
    }

    @Test
    void shouldPreserveIdAfterEditing() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);
        UUID originalId = product.getId();

        product.edit("Xiaomi 10", "222", null, category);

        assertEquals(originalId, product.getId());
    }

    @Test
    void shouldNormalizeDataWhenEditing() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);

        product.edit("  Xiaomi 10  ", "  222  ", "  New  ", category);

        assertEquals("Xiaomi 10", product.getName());
        assertEquals("222", product.getBarCode());
        assertEquals("New", product.getDescription());
    }

    @Test
    void shouldRejectEditWithNullName() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit(null, "222", null, category));
    }

    @Test
    void shouldRejectEditWithBlankName() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("   ", "222", null, category));
    }

    @Test
    void shouldRejectEditWithNullBarcode() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("Xiaomi 10", null, null, category));
    }

    @Test
    void shouldRejectEditWithBlankBarcode() {
        Product product = Product.create("Iphone 17", "111", null, category, UnitOfMeasure.UNIT);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("Xiaomi 10", "   ", null, category));
    }

    @Test
    void shouldPreserveDataWhenEditFails() {
        Product product = Product.create("Iphone 17", "111", "Pro", category, UnitOfMeasure.UNIT);

        assertThrows(IllegalArgumentException.class,
            () -> product.edit("   ", "222", "New", category));

        assertEquals("Iphone 17", product.getName());
        assertEquals("111", product.getBarCode());
        assertEquals("Pro", product.getDescription());
    }
}
