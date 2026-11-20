package marketcontrol.domain.repository;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import marketcontrol.domain.model.Product;

public interface ProductRepository {
	List<Product> findAll();
	List<ProductInventorySummary> findInventorySummary();

	record ProductInventorySummary(Product product, BigDecimal availableQuantity, long lotCount) {}
	Optional<Product> findProductById(UUID id);
	Optional<Product> findById(Connection conn, UUID id);
	Optional<Product> findByBarCode(String barCode);
	boolean existsByBarCode(String barCode);
	boolean existsByBarCodeAndIdNot(String barCode, UUID id);
	void save (Product product);
	void update(Product product);
}
