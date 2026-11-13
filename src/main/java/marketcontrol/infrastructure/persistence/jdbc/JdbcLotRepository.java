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
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;

public class JdbcLotRepository implements LotRepository{

	private final ProductRepository productRepository;
	
	public JdbcLotRepository(ProductRepository productRepository) {
		if(productRepository == null)
			throw new IllegalArgumentException("Repositório do produto não Instanciado.");
		
		this.productRepository = productRepository;
	}
	
	@Override
	public void save(Lot lot) {
		
		if(lot == null)
			throw new IllegalArgumentException("O lote não pode ser null.");
		
		String sql = "INSERT INTO lots (id, product_id, entry_date, expiration_date, initial_quantity, available_quantity) VALUES (?, ?, ?, ?, ?, ?)";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			
			Date entry = Date.valueOf(lot.getEntryDate());
			Date expires = Date.valueOf(lot.getExpirationDate());
			
			pstm.setString(1, lot.getId().toString());
			pstm.setString(2, lot.getProduct().getId().toString());
			pstm.setDate(3, entry);
			pstm.setDate(4, expires);
			pstm.setBigDecimal(5, lot.getInitialQuantity());
			pstm.setBigDecimal(6, lot.getAvailableQuantity());
			
			pstm.executeUpdate();
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível salvar esse lote.", e);
		}
	}

	@Override
	public Optional<Lot> findById(UUID id) {
		if (id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");

		String sql = "SELECT id, product_id, entry_date, expiration_date, initial_quantity, available_quantity FROM lots WHERE id = ?";

		UUID productId;
		LocalDate entry;
		LocalDate expires;
		BigDecimal initialQuantity;
		BigDecimal availableQuantity;

		try (Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)) {

			pstm.setString(1, id.toString());
			try (ResultSet rs = pstm.executeQuery()) {
				if (!rs.next())
					return Optional.empty();

				productId = UUID.fromString(rs.getString("product_id"));
				entry = rs.getObject("entry_date", LocalDate.class);
				expires = rs.getObject("expiration_date", LocalDate.class);
				initialQuantity = rs.getBigDecimal("initial_quantity");
				availableQuantity = rs.getBigDecimal("available_quantity");
			}
		} 
		catch (SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo ID do lote.", e);
		}
		
		ReadOnlyProduct p = productRepository.findById(productId)
				.orElseThrow(() -> new PersistenceException("O lote encontrado referencia um produto inexistente.",
						new IllegalStateException("Produto associado não encontrado.")));

		Lot lot = Lot.reconstitute(id, p, entry, expires, initialQuantity, availableQuantity);

		return Optional.of(lot);
	}

	@Override
	public List<Lot> findByProductId(UUID productId) {
		if (productId == null)
		    throw new IllegalArgumentException("O ID do produto é obrigatório.");
		
		Product product = productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
		
		String query = "SELECT id, entry_date, expiration_date, initial_quantity, available_quantity FROM lots WHERE product_id = ?";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(query)){
			
			pstm.setString(1, product.getId().toString());
			List<Lot> lots = new ArrayList<>();
			
			try(ResultSet rs = pstm.executeQuery()){

				while(rs.next()) {
					UUID uuid = UUID.fromString(rs.getString("id"));
					LocalDate entry = rs.getObject("entry_date", LocalDate.class);
					LocalDate expires = rs.getObject("expiration_date", LocalDate.class);
					BigDecimal initialQuantity = rs.getBigDecimal("initial_quantity");
					BigDecimal availableQuantity = rs.getBigDecimal("available_quantity");
					
					Lot lot = Lot.reconstitute(uuid, product, entry, expires, initialQuantity, availableQuantity);
					lots.add(lot);
				}
			}
			return lots;
		}
		catch(SQLException e) {
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
	public void update(Lot lot) {
		if(lot == null)
			throw new IllegalArgumentException("O Lote null não pode ser atualizado. Preencha as informações corretamente!");
		
		String sql = "UPDATE lots SET available_quantity = ? WHERE id = ?";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){

			
			pstm.setBigDecimal(1, lot.getAvailableQuantity());
			pstm.setString(2, lot.getId().toString());

			pstm.executeUpdate();
			
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível atualizar o Lote.", e);
		}	
	}
}
