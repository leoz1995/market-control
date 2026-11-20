package marketcontrol.http.server.infrastructure.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.StockMovementItem;
import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.exception.data.DataMappingException;

public class JdbcStockMovementRepository implements StockMovementRepository {

    @Override
    public void save(Connection conn, StockMovement stockMovement) {
        save(conn, stockMovement, false);
    }

    @Override
    public void saveInitialEntry(Connection conn, StockMovement stockMovement) {
        if (stockMovement == null || stockMovement.getType() != MovementType.ENTRY)
            throw new IllegalArgumentException("A origem do lote precisa ser uma movimentação ENTRY.");
        save(conn, stockMovement, true);
    }

    private void save(Connection conn, StockMovement stockMovement, boolean initialEntry) {

        if(conn == null)
            throw new IllegalArgumentException("Conexão obrigatória.");

        if (stockMovement == null)
            throw new IllegalArgumentException("A movimentação de stock é necessária.");

        String stock_movements_query = "INSERT INTO stock_movements (id, type, date_time, supplier_id, description) VALUES (?,?,?,?,?)";
        String stock_movement_items_query = "INSERT INTO stock_movement_items (id, stock_movement_id, lot_id, quantity, unit_cost, initial_entry_lot_id) VALUES (?,?,?,?,?,?)";

        try(PreparedStatement pstm = conn.prepareStatement(stock_movements_query)){

            Timestamp timestamp = Timestamp.valueOf(stockMovement.getDateTime());

            pstm.setString(1, stockMovement.getId().toString());
            pstm.setInt(2, stockMovement.getType().getCode());
            pstm.setTimestamp(3, timestamp);
            pstm.setString(4, stockMovement.getSupplier() == null ? null : stockMovement.getSupplier().getId().toString());
            pstm.setString(5, stockMovement.getDescription());

            pstm.executeUpdate();

            try (PreparedStatement items = conn.prepareStatement(stock_movement_items_query)){

                for(StockMovementItem item : stockMovement.getItems()) {
                    items.setString(1, item.getId().toString());
                    items.setString(2, stockMovement.getId().toString());
                    items.setString(3, item.getLotId().toString());
                    items.setBigDecimal(4, item.getQuantity());
                    items.setBigDecimal(5, item.getUnitCost());
                    items.setString(6, initialEntry ? item.getLotId().toString() : null);
                    items.executeUpdate();
                }
            }
        }
        catch(SQLException e) {
            throw new PersistenceException("Erro na conexão ou na transação.", e);
        }
    }

    @Override
    public Optional<BigDecimal> findInitialEntryByLotId(Connection conn, UUID lotId) {

        if(conn == null)
            throw new IllegalArgumentException("Conexão obrigatória.");

        if(lotId == null)
            throw new IllegalArgumentException("Id do Lote é inválido.");

        String query = "SELECT smi.unit_cost FROM stock_movement_items smi WHERE smi.initial_entry_lot_id = ?";

        try(PreparedStatement pstm = conn.prepareStatement(query)){

            pstm.setString(1, lotId.toString());

            try (ResultSet rs = pstm.executeQuery())
            {
                if (!rs.next())
                    return Optional.empty();

                BigDecimal unitCost = rs.getBigDecimal("unit_cost");

                if (rs.next())
                    throw new DataMappingException(
                        "Mais de uma entrada encontrada para o lote."
                    );

                return Optional.of(unitCost);
            }

        } catch (SQLException e) {
            throw new PersistenceException(
                    "Não foi possível recuperar o custo de aquisição do lote.",
                e
            );
        }
    }

}
