package marketcontrol.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.exception.data.DataMappingException;

class SupplierTest {
    private static Supplier valid() {
        return Supplier.create("  Example Legal  ", "  Example Trade ", "12345678901234", "SUPPLIER@EXAMPLE.COM", " 555-0100 ", " Alice ");
    }

    @Test void createsActiveSupplierAndNormalizesSupportedFields() {
        Supplier supplier = valid();
        assertNotNull(supplier.getId());
        assertTrue(supplier.isActive());
        assertEquals("example legal", supplier.getLegalName());
        assertEquals("example trade", supplier.getTradeName());
        assertEquals("supplier@example.com", supplier.getEmail());
        assertEquals("555-0100", supplier.getPhone());
        assertEquals(14, supplier.getDocument().length());
    }

    @Test void rejectsNullAndBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> Supplier.create(null, "Trade", "12345678901234", "a@b", "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create(" ", "Trade", "12345678901234", "a@b", "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("Legal", "Trade", null, "a@b", "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("Legal", "Trade", "12345678901234", null, "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("Legal", "Trade", "12345678901234", " ", "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("Legal", " ", "12345678901234", "a@b", "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("Legal", "Trade", "12345678901234", "a@b", " ", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("Legal", "Trade", "12345678901234", "a@b", "1", " "));
    }

    @Test void requiresFourteenNumericDocumentCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("L", "T", "123", "a@b", "1", "A"));
        assertThrows(IllegalArgumentException.class, () -> Supplier.create("L", "T", "1234567890123X", "a@b", "1", "A"));
    }

    @Test void supportsInactiveStateAndReconstitution() {
        Supplier supplier = valid();
        supplier.setActive(false);
        assertFalse(supplier.isActive());
        UUID id = UUID.randomUUID();
        Supplier restored = Supplier.reconstitute(id, "Legal", "Trade", "12345678901234", "a@b", "1", "A", false, LocalDateTime.now());
        assertEquals(id, restored.getId());
        assertFalse(restored.isActive());
        assertThrows(DataMappingException.class, () -> Supplier.reconstitute(null, "Legal", "Trade", "12345678901234", "a@b", "1", "A", true, LocalDateTime.now()));
    }

    @Test void editsDetailsWithoutChangingIdentityCreationTimeOrActiveState() {
        Supplier supplier = valid();
        supplier.setActive(false);
        UUID id = supplier.getId();
        LocalDateTime createdAt = supplier.getCreatedAt();

        supplier.edit("Updated Legal", "Updated Trade", "98765432109876", "NEW@EXAMPLE.COM", " 555-0200 ", " Bob ");

        assertAll(
                () -> assertEquals(id, supplier.getId()),
                () -> assertEquals(createdAt, supplier.getCreatedAt()),
                () -> assertFalse(supplier.isActive()),
                () -> assertEquals("updated legal", supplier.getLegalName()),
                () -> assertEquals("updated trade", supplier.getTradeName()),
                () -> assertEquals("98765432109876", supplier.getDocument()),
                () -> assertEquals("new@example.com", supplier.getEmail()),
                () -> assertEquals("555-0200", supplier.getPhone()),
                () -> assertEquals("bob", supplier.getContactName()));
    }

    @Test void invalidEditDoesNotPartiallyChangeSupplier() {
        Supplier supplier = valid();
        String originalName = supplier.getLegalName();
        String originalDocument = supplier.getDocument();

        assertThrows(IllegalArgumentException.class,
                () -> supplier.edit("Changed", "Trade", "123", "new@example.com", "555", "Contact"));

        assertEquals(originalName, supplier.getLegalName());
        assertEquals(originalDocument, supplier.getDocument());
    }
}
