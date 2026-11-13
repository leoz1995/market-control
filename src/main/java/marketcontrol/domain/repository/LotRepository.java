package marketcontrol.domain.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Lot;

public interface LotRepository {
	
	void save(Lot lot);
	Optional<Lot> findById(UUID id);
	List<Lot> findByProductId(UUID productId);
	BigDecimal getTotalAvailableQuantity(UUID productId);
	void update(Lot lot);
}
