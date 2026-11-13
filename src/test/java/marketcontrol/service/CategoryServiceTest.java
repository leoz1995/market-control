package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import marketcontrol.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.DatabaseTestCleanup;

class CategoryServiceTest {
    private CategoryService service;

    private String run = UUID.randomUUID().toString().substring(0, 8);
    private ReadOnlyCategory created;
    @BeforeEach void setUp() { service = new CategoryService(new JdbcCategoryRepository()); }
    @AfterEach void cleanUp() { if (created != null) DatabaseTestCleanup.deleteCategory(created.getId()); }

    @Test void shouldSaveAndFindCategoryById() {
        ReadOnlyCategory category = created = service.saveCategory("Nome " + run, "Description", true);
        assertNotNull(category);
        assertEquals("Nome " + run, service.findById(category.getId()).getName());
    }

    @Test void shouldEditCategoryAndReplaceDescriptionWithNull() {
        ReadOnlyCategory category = created = service.saveCategory("Beans " + run, "Feijão", true);
        ReadOnlyCategory edited = service.editCategory(category.getId(), "BeansTwo " + run, null, false);
        assertEquals("BeansTwo " + run, edited.getName());
        assertNull(edited.getDescription());
        assertFalse(edited.isActive());
    }
}
