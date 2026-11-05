
package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;

class ProductServiceTest {
	private ProductService service;
	private CategoryService categoryService;
	private ReadOnlyCategory category;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService();

        service = new ProductService(categoryService);

        category = categoryService.saveCategory(
            "Feijão", "Alimentação", true
        );
    }
    
    private ReadOnlyProduct createProduct(
        String name,
        String barcode,
        String description,
        boolean active
    ) {
        return service.saveProduct(name, barcode, description, active, category.getId());
    }

    @Test
    void shouldSaveProduct() {
        ReadOnlyProduct product = createProduct(
            "Iphone 17", "111", "Pro Max", true
        );

        assertNotNull(product);
        assertEquals("Iphone 17", product.getName());
        assertEquals("111", product.getBarCode());
        assertEquals(1, service.getProducts().size());
    }

    @Test
    void shouldRejectDuplicateBarcode() {
        createProduct("Iphone 17", "111", null, true);

        assertThrows(IllegalArgumentException.class,
            () -> createProduct("Iphone 18", "111", null, true));

        assertEquals(1, service.getProducts().size());
    }

    @Test
    void shouldRejectDuplicateBarcodeWithSpaces() {
        createProduct("Iphone 17", "111", null, true);

        assertThrows(IllegalArgumentException.class,
            () -> createProduct("Iphone 18", " 111 ", null, true));

        assertEquals(1, service.getProducts().size());
    }

    @Test
    void shouldSearchProductByBarcode() {
        createProduct("Iphone 17", "111", "Pro Max", true);

        ProductDTO result = service.searchBarCode("111");

        assertNotNull(result);
        assertEquals("Iphone 17", result.name());
        assertEquals("111", result.barCode());
        assertEquals("Pro Max", result.description());
        assertTrue(result.active());
    }

    @Test
    void shouldSearchBarcodeWithSpaces() {
        createProduct("Iphone 17", "111", null, true);

        ProductDTO result = service.searchBarCode(" 111 ");

        assertEquals("Iphone 17", result.name());
    }

    @Test
    void shouldRejectSearchWithNullBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> service.searchBarCode(null));
    }

    @Test
    void shouldRejectSearchWithBlankBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> service.searchBarCode("   "));
    }

    @Test
    void shouldRejectSearchWithUnknownBarcode() {
        assertThrows(IllegalArgumentException.class,
            () -> service.searchBarCode("999"));
    }


    @Test
    void shouldEditProduct() {
        ReadOnlyProduct original = createProduct(
            "Iphone 17", "111", "Pro", true
        );

        ProductDTO update = new ProductDTO(
            "Xiaomi 10", "222", "New Generation", false, category.getId()
        );

        ReadOnlyProduct edited = service.editProduct(original.getId(), update);

        assertEquals(original.getId(), edited.getId());
        assertEquals("Xiaomi 10", edited.getName());
        assertEquals("222", edited.getBarCode());
        assertEquals("New Generation", edited.getDescription());
        assertFalse(edited.isActive());
    }

    @Test
    void shouldKeepSameBarcodeWhenEditing() {
        ReadOnlyProduct original = createProduct(
            "Iphone 17", "111", null, true
        );

        ProductDTO update = new ProductDTO(
            "Xiaomi 10", "111", null, true, category.getId()
        );

        assertDoesNotThrow(
            () -> service.editProduct(original.getId(), update)
        );

        assertEquals("Xiaomi 10", service.searchBarCode("111").name());
    }

    @Test
    void shouldUpdateBarcodeIndexWhenEditing() {
        ReadOnlyProduct original = createProduct(
            "Iphone 17", "111", null, true
        );

        ProductDTO update = new ProductDTO(
            "Xiaomi 10", "222", null, true, category.getId()
        );

        service.editProduct(original.getId(), update);

        assertThrows(IllegalArgumentException.class,
            () -> service.searchBarCode("111"));

        assertEquals("Xiaomi 10", service.searchBarCode("222").name());
    }

    @Test
    void shouldRejectEditingWithBarcodeAlreadyUsedByAnotherProduct() {
        ReadOnlyProduct first = createProduct(
            "Iphone 17", "111", null, true
        );

        createProduct("Iphone 18", "222", null, true);

        ProductDTO update = new ProductDTO(
            "Xiaomi 10", "222", null, true, category.getId()
        );

        assertThrows(IllegalArgumentException.class,
            () -> service.editProduct(first.getId(), update));

        assertEquals("Iphone 17", service.searchBarCode("111").name());
        assertEquals("Iphone 18", service.searchBarCode("222").name());
    }

    @Test
    void shouldRejectEditingWithNullDTO() {
        ReadOnlyProduct product = createProduct(
            "Iphone 17", "111", null, true
        );

        assertThrows(IllegalArgumentException.class,
            () -> service.editProduct(product.getId(), null));
    }

    @Test
    void shouldRejectEditingUnknownProduct() {
        ProductDTO update = new ProductDTO(
            "Xiaomi 10", "222", null, true, category.getId()
        );

        assertThrows(IllegalArgumentException.class,
            () -> service.editProduct(UUID.randomUUID(), update));
    }

    @Test
    void shouldRejectEditingWithNullBarcode() {
        ReadOnlyProduct product = createProduct(
            "Iphone 17", "111", null, true
        );

        ProductDTO update = new ProductDTO(
            "Xiaomi 10", null, null, true, category.getId()
        );

        assertThrows(IllegalArgumentException.class,
            () -> service.editProduct(product.getId(), update));

        assertEquals("Iphone 17", service.searchBarCode("111").name());
    }

    @Test
    void shouldRejectEditingWithBlankBarcode() {
        ReadOnlyProduct product = createProduct(
            "Iphone 17", "111", null, true
        );

        ProductDTO update = new ProductDTO(
            "Xiaomi 10", "   ", null, true, category.getId()
        );

        assertThrows(IllegalArgumentException.class,
            () -> service.editProduct(product.getId(), update));

        assertEquals("Iphone 17", service.searchBarCode("111").name());
    }

    @Test
    void shouldRejectEditingWithInvalidName() {
        ReadOnlyProduct product = createProduct(
            "Iphone 17", "111", null, true
        );

        ProductDTO update = new ProductDTO(
            "   ", "222", null, false, category.getId()
        );

        assertThrows(IllegalArgumentException.class,
            () -> service.editProduct(product.getId(), update));

        assertEquals("Iphone 17", service.searchBarCode("111").name());
        assertThrows(IllegalArgumentException.class,
            () -> service.searchBarCode("222"));
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoProducts() {
        List<ReadOnlyProduct> products = service.getProducts();

        assertNotNull(products);
        assertTrue(products.isEmpty());
    }

    @Test
    void shouldReturnAllProducts() {
        createProduct("Iphone 17", "111", null, true);
        createProduct("Iphone 18", "222", null, true);
        createProduct("Xiaomi 10", "333", null, true);

        assertEquals(3, service.getProducts().size());
    }

    @Test
    void shouldPreserveInsertionOrder() {
        createProduct("Iphone 17", "111", null, true);
        createProduct("Iphone 18", "222", null, true);
        createProduct("Xiaomi 10", "333", null, true);

        List<ReadOnlyProduct> products = service.getProducts();

        assertEquals("Iphone 17", products.get(0).getName());
        assertEquals("Iphone 18", products.get(1).getName());
        assertEquals("Xiaomi 10", products.get(2).getName());
    }

    @Test
    void shouldReturnImmutableProductList() {
        createProduct("Iphone 17", "111", null, true);

        List<ReadOnlyProduct> products = service.getProducts();

        assertThrows(
        	    UnsupportedOperationException.class,
        	    products::clear
        	);
    }
    
    @Test
    void shouldRejectEditingWithDuplicateBarcodeWithSpaces() {
    	
        ReadOnlyProduct p1 = createProduct(
                "Iphone 18", "111", "Pro", true
            );
        ReadOnlyProduct p2 = createProduct(
                "Iphone 18", "222", "Pro", true
            );
        
        ProductDTO update = new ProductDTO(
                "Iphone 18", "111", "Pro", false, p2.getCategory().getId()
            );
        
        assertThrows(IllegalArgumentException.class,
                () -> service.editProduct(p2.getId(), update));
    }
    
    @Test
    void shouldKeepProductDataWhenBarcodeIsDuplicated() {
        ReadOnlyProduct p1 = createProduct(
                "Iphone 17", "111", "Pro", true
            );
        createProduct(
                "Xiamo 17", "222", "Max", true
            );
        
        
        ProductDTO update = new ProductDTO(
                "Iphone 17", "222", "Pro", false, category.getId()
            );

        assertThrows(IllegalArgumentException.class,
                () -> service.editProduct(p1.getId(), update));

        assertEquals("Iphone 17", p1.getName());
        assertEquals("111", p1.getBarCode());
        assertEquals("Pro", p1.getDescription());
        assertTrue(p1.isActive());
    }
    
    @Test
    void shouldReturnUpdatedProductAfterMultipleEdits() {
        ReadOnlyProduct p1 = createProduct(
                "Iphone 17", "111", "Pro", true
            );
        
        ProductDTO update = new ProductDTO(
                "Xiaomi 10", "222", "Max", false, category.getId()
            );

        ProductDTO update2 = new ProductDTO(
                "Samsung S26", "333", "Owner", false, category.getId()
            );

        service.editProduct(p1.getId(), update);
        service.editProduct(p1.getId(), update2);
        
        ProductDTO result3 = service.searchBarCode("333");

        assertThrows(IllegalArgumentException.class,
                () -> service.searchBarCode("111"));
        assertThrows(IllegalArgumentException.class,
                () -> service.searchBarCode("222"));
        
        assertEquals("Samsung S26", result3.name());
    }
}
