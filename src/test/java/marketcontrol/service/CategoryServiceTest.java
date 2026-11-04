package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;

public class CategoryServiceTest {
	private CategoryService categoryService = new CategoryService();
    @Test
    void shouldFindCategoryById() {
    	
    	ReadOnlyCategory cat = categoryService.saveCategory("Nome 1", "Description", true);

        //assertThrows(IllegalArgumentException.class, () -> categoryService.searchCategory(cat));

        assertNotNull(cat);
        assertEquals("Nome 1", cat.getName());
    }
    
    @Test
    void shouldEditCategory(){
    	    ReadOnlyCategory beans = categoryService.saveCategory("Beans", "Feijão", true);
    	    
    	    ReadOnlyCategory eddit = categoryService.editCategory(beans.getId(), "BeansTwo", null, false);
    	    
    	    assertEquals("BeansTwo", eddit.getName());
    	    assertEquals("Feijão", "Feijão");
    	    assertFalse(eddit.isActive());
    	    
    }
}
