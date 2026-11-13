package marketcontrol.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;

public class JdbcProductRepository implements ProductRepository{

	@Override
	public List<Product> findAll() {
		String sql = """
			SELECT
			    p.id,
			    p.name,
			    p.bar_code,
			    p.description,
			    p.unit,
			    c.id AS category_id,
			    c.name AS category_name,
			    c.description AS category_description,
			    c.active AS category_active
			FROM products p
			INNER JOIN categories c ON c.id = p.category_id
			""";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql);
				ResultSet rs = pstm.executeQuery()){
			
			List<Product> products = new ArrayList<>();

			while(rs.next()) {
				products.add(mapRow(rs));
			}
			return products;
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca dos produtos.", e);
		}
	}

	@Override
	public Optional<Product> findById(UUID id) {
		if(id == null)
			throw new IllegalArgumentException("Id null, não é possível realizar a busca.");
		
		String sql = """
				SELECT
			    p.id,
			    p.name,
			    p.bar_code,
			    p.description,
			    p.unit,
			    c.id AS category_id,
			    c.name AS category_name,
			    c.description AS category_description,
			    c.active AS category_active
			FROM products p
			INNER JOIN categories c ON c.id = p.category_id
			WHERE p.id = ?;
				""";
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql))
		{
			pstm.setString(1, id.toString());

			try(ResultSet rs = pstm.executeQuery()){
				
				if(!rs.next())
					return Optional.empty();
				
				return Optional.of(mapRow(rs));
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo Id do produto.", e);
		}
	}
	
	@Override
	public Optional<Product> findByBarCode(String barCode) {
		if(barCode == null || barCode.isBlank())
		    throw new IllegalArgumentException("Código de barras inválido.");
		
		String sql = """
				SELECT
			    p.id,
			    p.name,
			    p.bar_code,
			    p.description,
			    p.unit,
			    c.id AS category_id,
			    c.name AS category_name,
			    c.description AS category_description,
			    c.active AS category_active
			FROM products p
			INNER JOIN categories c ON c.id = p.category_id
			WHERE p.bar_code = ?;
				""";
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql))
		{
			pstm.setString(1, barCode.trim().toUpperCase(Locale.ROOT));
			try(ResultSet rs = pstm.executeQuery()){
				
				if(!rs.next())
					return Optional.empty();
				
				return Optional.of(mapRow(rs));
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo código do produto.", e);
		}
	}

	@Override
	public boolean existsByBarCode(String barCode) {
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("O código do produto é obrigatório.");
		
		String sql = "SELECT 1 FROM products WHERE bar_code = ?";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			pstm.setString(1, barCode.trim().toUpperCase(Locale.ROOT));
			try(ResultSet rs = pstm.executeQuery()){
				return rs.next();
			}
			
		}catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo código do produto.", e);
		}
	}

	@Override
	public boolean existsByBarCodeAndIdNot(String barCode, UUID id) {
		if(id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");
		
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("O código do produto é obrigatório.");
		
		String sql = "SELECT 1 FROM products WHERE bar_code = ? AND id <> ?";
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){

			pstm.setString(1, barCode.trim().toUpperCase(Locale.ROOT));
			pstm.setString(2, id.toString());
			try(ResultSet rs = pstm.executeQuery()){	
				return rs.next();
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível verificar a existência de outro produto com esse código.", e);
		}
	}

	@Override
	public void save(Product product) {
		if(product == null)
			throw new IllegalArgumentException("O produto não pode ser null.");
		
		String sql = "INSERT INTO products (id, name, bar_code, description, category_id, unit) VALUES (?, ?, ?, ?,? ,?)";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			
			pstm.setString(1, product.getId().toString());
			pstm.setString(2, product.getName());
			pstm.setString(3, product.getBarCode());
			pstm.setString(4, product.getDescription());
			pstm.setString(5, product.getCategory().getId().toString());
			pstm.setInt(6, product.getUnit().getCode());
			
			pstm.executeUpdate();
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível salvar esse produto.", e);
		}
	}

	@Override
	public void update(Product product) {
		if(product == null)
			throw new IllegalArgumentException("O produto null não pode ser atualizado. Preencha as informações corretamente!");
		
		String sql = "UPDATE products SET name = ?, bar_code = ?, description = ?, category_id = ?, unit = ? WHERE id = ?";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){

			pstm.setString(1, product.getName());
			pstm.setString(2, product.getBarCode());
			pstm.setString(3, product.getDescription());
			pstm.setString(4, product.getCategory().getId().toString());
			pstm.setInt(5, product.getUnit().getCode());
			pstm.setString(6, product.getId().toString());

			pstm.executeUpdate();
			
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível atualizar o produto.", e);
		}
	}

	private Product mapRow(ResultSet rs) throws SQLException {
		UUID id = UUID.fromString(rs.getString("id"));
		String name = rs.getString("name");
		String barCode = rs.getString("bar_code");
		String description = rs.getString("description");

		UUID categoryId = UUID.fromString(rs.getString("category_id"));
		String categoryName = rs.getString("category_name");
		String categoryDescription = rs.getString("category_description");
		boolean categoryActive = rs.getBoolean("category_active");

		ReadOnlyCategory cat = Category.reconstitute(categoryId, categoryName, categoryDescription, categoryActive);

		int unitCode = rs.getInt("unit");
		UnitOfMeasure unit = UnitOfMeasure.fromCode(unitCode);

		return Product.reconstitute(id, name, barCode, description, cat, unit);
	}
}
