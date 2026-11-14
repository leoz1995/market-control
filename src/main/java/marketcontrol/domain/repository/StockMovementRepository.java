package marketcontrol.domain.repository;

import java.sql.Connection;

import marketcontrol.domain.model.StockMovement;

public interface StockMovementRepository {

	void save(Connection conn, StockMovement stockMovement);
}
