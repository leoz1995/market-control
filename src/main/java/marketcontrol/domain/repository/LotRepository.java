package marketcontrol.domain.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlySupplier;

public interface LotRepository {
	
	void saveWithInitialEntry(Connection conn, Lot lot, ReadOnlySupplier supplier, BigDecimal unitCost);
	Optional<Lot> findById(UUID id);
	Optional<ReadOnlyLot> findById(Connection conn, UUID id);
	List<Lot> findByProductId(UUID productId);
	BigDecimal getTotalAvailableQuantity(UUID productId);
	int decreaseAvailableQuantityForSale(Connection conn, UUID uuid, BigDecimal quantity);
	int decreaseAvailableQuantityForLoss(Connection conn, UUID uuid, BigDecimal quantity);
}
