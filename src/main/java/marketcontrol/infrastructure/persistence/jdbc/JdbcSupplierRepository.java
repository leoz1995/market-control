package marketcontrol.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;

public class JdbcSupplierRepository implements SupplierRepository {

    @Override
    public void save(Supplier supplier) {
        if(supplier == null)
            throw new IllegalArgumentException("O Supplier não pode ser null.");

        String sql = "INSERT INTO suppliers (id, legal_name, trade_name, document, email, phone, contact_name, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try(Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstm = conn.prepareStatement(sql)){

            pstm.setString(1, supplier.getId().toString());
            pstm.setString(2, supplier.getLegalName());
            pstm.setString(3, supplier.getTradeName());
            pstm.setString(4, supplier.getDocument());
            pstm.setString(5, supplier.getEmail());
            pstm.setString(6, supplier.getPhone());
            pstm.setString(7, supplier.getContactName());
            pstm.setBoolean(8, supplier.isActive());
            pstm.setTimestamp(9, Timestamp.valueOf(supplier.getCreatedAt()));

            pstm.executeUpdate();
        }
        catch(SQLException e) {
            throw new PersistenceException("Não foi possível salvar esse Supplier.", e);
        }
    }

    @Override
    public Optional<Supplier> findById(UUID uuid) {
        if(uuid == null)
            throw new IllegalArgumentException("O ID do Supplier é obrigatório.");

        String sql = "SELECT id, legal_name, trade_name, document, email, phone, contact_name, active, created_at FROM suppliers WHERE id = ?";
        try(Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstm = conn.prepareStatement(sql)){

            pstm.setString(1, uuid.toString());
            try(ResultSet rs = pstm.executeQuery()){

                if(!rs.next())
                    return Optional.empty();

                UUID id = UUID.fromString(rs.getString("id"));
                String legalName = rs.getString("legal_name");
                String tradeName = rs.getString("trade_name");
                String document = rs.getString("document");
                String email = rs.getString("email");
                String phone = rs.getString("phone");
                String contactName = rs.getString("contact_name");
                boolean active = rs.getBoolean("active");
                LocalDateTime createdAt = rs.getObject("created_at", LocalDateTime.class);

                Supplier supplier = Supplier.reconstitute(id, legalName, tradeName, document, email, phone, contactName, active, createdAt);

                return Optional.of(supplier);
            }
        }
        catch(SQLException e) {
            throw new PersistenceException("Não foi possível realizar a busca do Supplier pelo ID.", e);
        }
    }

    @Override
    public boolean existsByDocument(String document) {
        if(document == null || document.isBlank())
            throw new IllegalArgumentException("O documento do supplier é obrigatório.");

        String sql = "SELECT 1 FROM suppliers WHERE document = ?";

        try(Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstm = conn.prepareStatement(sql)){
            pstm.setString(1, document.trim().toUpperCase(Locale.ROOT));
            try(ResultSet rs = pstm.executeQuery()){
                return rs.next();
            }

        }
        catch(SQLException e) {
            throw new PersistenceException("Falha ao verificar existência de supplier por documento.", e);
        }
    }

    @Override
    public void update(Supplier supplier) {
        if(supplier == null)
            throw new IllegalArgumentException("O fornecedor não pode ser null.");

        String sql = "UPDATE suppliers SET legal_name = ?, trade_name = ?, document = ?, email = ?, phone = ?, contact_name = ?, active = ? WHERE id = ?";

        try(Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstm = conn.prepareStatement(sql)){

            pstm.setString(1, supplier.getLegalName());
            pstm.setString(2, supplier.getTradeName());
            pstm.setString(3, supplier.getDocument());
            pstm.setString(4, supplier.getEmail());
            pstm.setString(5, supplier.getPhone());
            pstm.setString(6, supplier.getContactName());
            pstm.setBoolean(7, supplier.isActive());
            pstm.setString(8, supplier.getId().toString());

            int affectedRows = pstm.executeUpdate();
            if(affectedRows == 0 && !existsById(conn, supplier.getId()))
                throw new IllegalArgumentException("Fornecedor não encontrado para atualização.");
        }
        catch(SQLException e) {
            throw new PersistenceException("Não foi possível atualizar esse Supplier.", e);
        }
    }

    private boolean existsById(Connection conn, UUID id) throws SQLException {
        try(PreparedStatement pstm = conn.prepareStatement("SELECT 1 FROM suppliers WHERE id = ?")){
            pstm.setString(1, id.toString());
            try(ResultSet rs = pstm.executeQuery()){
                return rs.next();
            }
        }
    }

}
