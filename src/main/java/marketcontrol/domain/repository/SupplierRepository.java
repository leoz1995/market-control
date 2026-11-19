package marketcontrol.domain.repository;

import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Supplier;

public interface SupplierRepository {

	void save(Supplier supplier);
	Optional<Supplier> findById(UUID uuid);
	boolean existsByDocument(String document);
	void update(Supplier supplier);
}
