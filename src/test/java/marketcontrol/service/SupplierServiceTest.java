package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.exception.ResourceNotFoundException;

class SupplierServiceTest {
    @Test void savesSupplierAndRejectsDuplicateDocumentBeforeInsert() {
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger lookups = new AtomicInteger();
        SupplierRepository repository = (SupplierRepository) Proxy.newProxyInstance(SupplierRepository.class.getClassLoader(), new Class<?>[]{SupplierRepository.class}, (p,m,a)-> {
            if (m.getName().equals("existsByDocument")) { lookups.incrementAndGet(); return "11111111111111".equals(a[0]); }
            if (m.getName().equals("save")) { saves.incrementAndGet(); return null; }
            if (m.getName().equals("findById")) return Optional.empty();
            return null;
        });
        SupplierService service = new SupplierService(repository);
        Supplier supplier = service.save("Legal", "Trade", "22222222222222", "a@b", "123", "Person");
        assertTrue(supplier.isActive());
        assertEquals(1, saves.get());
        assertThrows(IllegalArgumentException.class, () -> service.save("Legal", "Trade", "11111111111111", "a@b", "123", "Person"));
        assertEquals(1, saves.get());
        assertEquals(2, lookups.get());
    }

    @Test void validatesFindAndDocumentLookupArguments() {
        SupplierRepository repository = (SupplierRepository) Proxy.newProxyInstance(SupplierRepository.class.getClassLoader(), new Class<?>[]{SupplierRepository.class}, (p,m,a)-> m.getName().equals("existsByDocument") ? false : Optional.empty());
        SupplierService service = new SupplierService(repository);
        assertThrows(IllegalArgumentException.class, () -> service.existsByDocument(" "));
        assertThrows(IllegalArgumentException.class, () -> service.findById(null));
        assertThrows(ResourceNotFoundException.class, () -> service.findById(UUID.randomUUID()));
    }

    @Test void updatesSupplierAndRejectsDuplicateDocument() {
        MemorySupplierRepository repository = new MemorySupplierRepository();
        Supplier first = supplier("11111111111111", "First");
        Supplier second = supplier("22222222222222", "Second");
        repository.save(first);
        repository.save(second);
        SupplierService service = new SupplierService(repository);

        var updated = service.update(first.getId(), "Updated Legal", "Updated Trade", "33333333333333", "updated@example.com", "555", "New Contact");
        assertEquals("Updated Legal".toLowerCase(), updated.getLegalName());
        assertEquals("33333333333333", repository.findById(first.getId()).orElseThrow().getDocument());
        assertEquals(first.getCreatedAt(), updated.getCreatedAt());
        assertThrows(IllegalArgumentException.class,
                () -> service.update(first.getId(), "Changed", "Trade", second.getDocument(), "a@b", "1", "Person"));
        assertEquals("updated legal", repository.findById(first.getId()).orElseThrow().getLegalName());
    }

    @Test void deactivatesSupplierAndPersistsStateThroughRepository() {
        MemorySupplierRepository repository = new MemorySupplierRepository();
        Supplier active = supplier("12345678901234", "Active");
        repository.save(active);

        var inactive = new SupplierService(repository).deactivate(active.getId());

        assertFalse(inactive.isActive());
        assertFalse(repository.findById(active.getId()).orElseThrow().isActive());
    }

    @Test void updateAndDeactivationRejectMissingSupplier() {
        SupplierService service = new SupplierService(new MemorySupplierRepository());
        UUID unknownId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.update(unknownId, "Legal", "Trade", "12345678901234", "a@b", "1", "Person"));
        assertThrows(IllegalArgumentException.class, () -> service.deactivate(unknownId));
        assertThrows(IllegalArgumentException.class, () -> service.deactivate(null));
    }

    @Test void failedPersistenceLeavesStoredSupplierUnchanged() {
        MemorySupplierRepository repository = new MemorySupplierRepository();
        Supplier active = supplier("12345678901234", "Original");
        repository.save(active);
        repository.failUpdate = true;

        assertThrows(PersistenceException.class, () -> new SupplierService(repository).deactivate(active.getId()));

        Supplier persisted = repository.findById(active.getId()).orElseThrow();
        assertTrue(persisted.isActive());
        assertEquals("original", persisted.getLegalName());
    }

    private static Supplier supplier(String document, String name) {
        return Supplier.create(name, "Trade", document, "supplier@example.com", "555", "Contact");
    }

    private static final class MemorySupplierRepository implements SupplierRepository {
        private final Map<UUID, Supplier> suppliers = new HashMap<>();
        private boolean failUpdate;

        @Override public void save(Supplier supplier) { suppliers.put(supplier.getId(), copy(supplier)); }

        @Override public Optional<Supplier> findById(UUID id) {
            return Optional.ofNullable(suppliers.get(id)).map(MemorySupplierRepository::copy);
        }

        @Override public boolean existsByDocument(String document) {
            return suppliers.values().stream().anyMatch(supplier -> supplier.getDocument().equals(document.trim()));
        }

        @Override public void update(Supplier supplier) {
            if(failUpdate) throw new PersistenceException("controlled persistence failure");
            if(!suppliers.containsKey(supplier.getId())) throw new IllegalArgumentException("Fornecedor não encontrado.");
            suppliers.put(supplier.getId(), copy(supplier));
        }

        private static Supplier copy(Supplier supplier) {
            return Supplier.reconstitute(supplier.getId(), supplier.getLegalName(), supplier.getTradeName(), supplier.getDocument(),
                    supplier.getEmail(), supplier.getPhone(), supplier.getContactName(), supplier.isActive(), supplier.getCreatedAt());
        }
    }
}
