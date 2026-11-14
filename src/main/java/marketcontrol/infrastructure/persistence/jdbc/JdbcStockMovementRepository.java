package marketcontrol.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.StockMovementItem;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.exception.PersistenceException;

public class JdbcStockMovementRepository implements StockMovementRepository {

	@Override
	public void save(Connection conn, StockMovement stockMovement) {
		String stock_movements_query = "INSERT INTO stock_movements (id, type, date_time, description) VALUES (?,?,?,?)";
		String stock_movement_items_query = "INSERT INTO stock_movement_items (id, stock_movement_id, lot_id, quantity) VALUES (?,?,?,?)";
		
		try(PreparedStatement pstm = conn.prepareStatement(stock_movements_query)){
			
			Timestamp timestamp = Timestamp.valueOf(stockMovement.getDateTime());
			
			pstm.setString(1, stockMovement.getId().toString());
			pstm.setInt(2, stockMovement.getType().getCode());
			pstm.setTimestamp(3, timestamp);
			pstm.setString(4, stockMovement.getDescription());

			pstm.executeUpdate();
			
			try (PreparedStatement items = conn.prepareStatement(stock_movement_items_query)){
			
				for(StockMovementItem item : stockMovement.getItems()) {
					items.setString(1, item.getId().toString());
					items.setString(2, stockMovement.getId().toString());
					items.setString(3, item.getLotId().toString());
					items.setBigDecimal(4, item.getQuantity());
					items.executeUpdate();
				}
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Erro na conexão ou na transação.", e);
		}
	}

}
