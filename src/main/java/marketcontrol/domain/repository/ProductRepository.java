package marketcontrol.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Product;

public interface ProductRepository {
	List<Product> findAll();
	Optional<Product> findById(UUID id);
	Optional<Product> findByBarCode(String barCode);
	boolean existsByBarCode(String barCode);
	boolean existsByBarCodeAndIdNot(String barCode, UUID id);
	void save (Product product);
	void update(Product product);
}
