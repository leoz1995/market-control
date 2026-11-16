package marketcontrol.infrastructure.persistence.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.exception.data.DataMappingException;
import marketcontrol.infrastructure.database.DatabaseConnection;

public class JdbcLotRepository implements LotRepository{

	private final ProductRepository productRepository;
	
	public JdbcLotRepository(ProductRepository productRepository) {
		if(productRepository == null)
			throw new IllegalArgumentException("Repositório do produto não Instanciado.");
		
		this.productRepository = productRepository;
	}
	
	@Override
	public void save(Connection conn, Lot lot) {
		
		if(conn == null)
			throw new IllegalArgumentException("A conexão é obrigatória.");
		
		if(lot == null)
			throw new IllegalArgumentException("O lote não pode ser null.");
		
		String sql = "INSERT INTO lots (id, product_id, entry_date, expiration_date, total_received_quantity, available_quantity) VALUES (?, ?, ?, ?, ?, ?)";
		
		try(PreparedStatement pstm = conn.prepareStatement(sql)){
			
			Date entry = Date.valueOf(lot.getEntryDate());
			Date expires = Date.valueOf(lot.getExpirationDate());
			
			pstm.setString(1, lot.getId().toString());
			pstm.setString(2, lot.getProduct().getId().toString());
			pstm.setDate(3, entry);
			pstm.setDate(4, expires);
			pstm.setBigDecimal(5, lot.getTotalReceivedQuantity());
			pstm.setBigDecimal(6, lot.getAvailableQuantity());
			
			int affectedRows = pstm.executeUpdate();
			if(affectedRows != 1)
				throw new PersistenceException("A inserção do lote não afetou exatamente uma linha. Linhas afetadas: "+affectedRows);
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível salvar esse lote.", e);
		}
	}

	@Override
	public Optional<Lot> findById(UUID id) {
		if (id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");

		try (Connection conn = DatabaseConnection.getConnection()) {
			return findLot(conn,id);
		} 
		catch (SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo ID do lote.", e);
		}
	}

	@Override
	public Optional<ReadOnlyLot> findById(Connection conn, UUID id) {
		
		if(conn == null)
			throw new IllegalArgumentException("A conexão é obrigatória.");
		
		if (id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");

		return findLot(conn,id).map(lot -> (ReadOnlyLot) lot);
	}
	
	@Override
	public List<Lot> findByProductId(UUID productId) {
		if (productId == null)
			throw new IllegalArgumentException("O ID do produto é obrigatório.");

		String query = "SELECT id, entry_date, expiration_date, total_received_quantity, available_quantity FROM lots WHERE product_id = ?";

		try (Connection conn = DatabaseConnection.getConnection()) {

			Product product = productRepository.findById(conn, productId)
					.orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));

			try (PreparedStatement pstm = conn.prepareStatement(query)) {
				
				pstm.setString(1, product.getId().toString());
				List<Lot> lots = new ArrayList<>();

				try (ResultSet rs = pstm.executeQuery()) {

					while (rs.next()) {
						Lot lot = mapRow(rs, product);
						lots.add(lot);
					}
				}
				return lots;
			}
		} catch (SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo ID do produto.", e);
		}
	}

	@Override
	public BigDecimal getTotalAvailableQuantity(UUID productId) {
		if(productId == null)
			throw new IllegalArgumentException("Id do produto é inválido.");
		
		String query = "SELECT COALESCE(SUM(available_quantity), 0) AS total_available FROM lots WHERE product_id = ? AND expiration_date >= CURRENT_DATE";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(query)){

			BigDecimal availableQuantity = BigDecimal.ZERO; 
			
			pstm.setString(1, productId.toString());
			try (ResultSet rs = pstm.executeQuery()){
				if(rs.next()) 
				{
					availableQuantity =  rs.getBigDecimal("total_available");
				}
				return availableQuantity;
			}
			
	    } catch (SQLException e) {
	        throw new PersistenceException(
	            "Não foi possível calcular o estoque disponível.",
	            e
	        );
	    }
	}

	@Override
	public int decreaseAvailableQuantityForSale(Connection conn, UUID uuid, BigDecimal quantity) {
		
		if(conn == null)
			throw new IllegalArgumentException("A conexão é obrigatória.");
		
		if(uuid == null)
			throw new IllegalArgumentException("UUID null, preencha corretamente.");
			
		if(quantity == null)
			throw new IllegalArgumentException("Quantidade nulla, preencha corretamente.");
		
		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade não pode ser zero ou negativo.");
		
		String sql = "UPDATE lots SET available_quantity = available_quantity - ? WHERE id = ? AND available_quantity >= ? AND expiration_date >= CURRENT_DATE";
		
		try(PreparedStatement pstm = conn.prepareStatement(sql)){
			
			pstm.setBigDecimal(1, quantity);
			pstm.setString(2, uuid.toString());
			pstm.setBigDecimal(3, quantity);
			
			return pstm.executeUpdate();
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível atualizar o Lote.", e);
		}
	}

	@Override
	public int decreaseAvailableQuantityForLoss(Connection conn, UUID uuid, BigDecimal quantity) {
		
		if(conn == null)
			throw new IllegalArgumentException("A conexão é obrigatória.");
		
		if(uuid == null)
			throw new IllegalArgumentException("UUID null, preencha corretamente.");
			
		if(quantity == null)
			throw new IllegalArgumentException("Quantidade nulla, preencha corretamente.");
		
		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade não pode ser zero ou negativo.");
		
		String sql = "UPDATE lots SET available_quantity = available_quantity - ? WHERE id = ? AND available_quantity >= ?";
		
		try(PreparedStatement pstm = conn.prepareStatement(sql)){
			
			pstm.setBigDecimal(1, quantity);
			pstm.setString(2, uuid.toString());
			pstm.setBigDecimal(3, quantity);

			return pstm.executeUpdate();	
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível atualizar o Lote.", e);
		}
	}

	@Override
	public int increaseAvailableQuantityForAdditionalEntry(Connection conn, UUID uuid, BigDecimal quantity) {
		
		if(conn == null)
			throw new IllegalArgumentException("A conexão é obrigatória.");
		
		if(uuid == null)
			throw new IllegalArgumentException("UUID null, preencha corretamente.");
			
		if(quantity == null)
			throw new IllegalArgumentException("Quantidade nulla, preencha corretamente.");
		
		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade não pode ser zero ou negativo.");
		
		String query = "UPDATE lots SET total_received_quantity = total_received_quantity + ?, available_quantity = available_quantity + ? WHERE id = ? AND expiration_date >= CURRENT_DATE";
		
		try(PreparedStatement pstm = conn.prepareStatement(query)){

			pstm.setBigDecimal(1, quantity);
			pstm.setBigDecimal(2, quantity);
			pstm.setString(3, uuid.toString());
			
			return pstm.executeUpdate();
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível salvar a nova entrada.", e);
		}
	}
	
	private Optional<Lot> findLot(Connection conn, UUID id){
		String sql = "SELECT id, product_id, entry_date, expiration_date, total_received_quantity, available_quantity FROM lots WHERE id = ?";
		
	    UUID productId;
	    LocalDate entry;
	    LocalDate expires;
	    BigDecimal totalReceived;
	    BigDecimal available;

	    try (PreparedStatement pstm = conn.prepareStatement(sql)) {
	        pstm.setString(1, id.toString());

	        try (ResultSet rs = pstm.executeQuery()) {
	            if (!rs.next())
	                return Optional.empty();

	            productId = UUID.fromString(rs.getString("product_id"));
	            entry = rs.getObject("entry_date", LocalDate.class);
	            expires = rs.getObject("expiration_date", LocalDate.class);
	            totalReceived = rs.getBigDecimal("total_received_quantity");
	            available = rs.getBigDecimal("available_quantity");
	        }

	        ReadOnlyProduct product = productRepository.findById(conn, productId)
	            .orElseThrow(() -> new DataMappingException(
	                "O lote encontrado referencia um produto inexistente."));

	        return Optional.of(Lot.reconstitute(id, product, entry, expires, totalReceived, available));
		}
		catch (SQLException e) {
	        throw new PersistenceException(
	                "Não foi possível realizar a busca pelo ID do lote.", e
	            );
	        }
	}
	
	private Lot mapRow(ResultSet rs, ReadOnlyProduct product) throws SQLException {
		try {
	        UUID id = UUID.fromString(rs.getString("id"));

	        LocalDate entry = rs.getObject("entry_date", LocalDate.class);
	        LocalDate expires = rs.getObject("expiration_date", LocalDate.class);

	        BigDecimal totalReceived = rs.getBigDecimal("total_received_quantity");

	        BigDecimal available = rs.getBigDecimal("available_quantity");

	        return Lot.reconstitute(id, product, entry, expires, totalReceived, available);
		}
		catch(IllegalArgumentException e) {
	        throw new DataMappingException("Falha ao mapear os dados persistidos para a entidade Lot.", e);
		}
	}
}
