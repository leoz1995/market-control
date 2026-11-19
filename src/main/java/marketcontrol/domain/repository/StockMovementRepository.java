package marketcontrol.domain.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.StockMovement;

public interface StockMovementRepository {

	void save(Connection conn, StockMovement stockMovement);
	void saveInitialEntry(Connection conn, StockMovement stockMovement);
	Optional<BigDecimal> findInitialEntryByLotId(Connection conn, UUID lotId);
}
