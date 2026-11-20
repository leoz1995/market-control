package marketcontrol.http.server.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
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
import marketcontrol.domain.repository.ProductRepository.ProductInventorySummary;
import marketcontrol.exception.PersistenceException;
import marketcontrol.exception.data.DataMappingException;
import marketcontrol.http.server.infrastructure.database.DatabaseConnection;

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
			throw new PersistenceException("Falha ao consultar todos os produtos.", e);
		}
	}

	@Override
	public List<ProductInventorySummary> findInventorySummary() {
		String sql = """
			SELECT p.id, p.name, p.bar_code, p.description, p.unit,
			       c.id AS category_id, c.name AS category_name,
			       c.description AS category_description, c.active AS category_active,
			       COALESCE(SUM(CASE WHEN l.expiration_date >= CURRENT_DATE THEN l.available_quantity ELSE 0 END), 0) AS available_quantity,
			       COUNT(l.id) AS lot_count
			FROM products p
			INNER JOIN categories c ON c.id = p.category_id
			LEFT JOIN lots l ON l.product_id = p.id
			GROUP BY p.id, p.name, p.bar_code, p.description, p.unit,
			         c.id, c.name, c.description, c.active
			ORDER BY p.name
			""";
		try (Connection conn = DatabaseConnection.getConnection();
		     PreparedStatement statement = conn.prepareStatement(sql);
		     ResultSet rs = statement.executeQuery()) {
			List<ProductInventorySummary> summaries = new ArrayList<>();
			while (rs.next()) {
				summaries.add(new ProductInventorySummary(mapRow(rs),
						rs.getBigDecimal("available_quantity"), rs.getLong("lot_count")));
			}
			return summaries;
		} catch (SQLException e) {
			throw new PersistenceException("Falha ao consultar o resumo de estoque dos produtos.", e);
		}
	}
	
	@Override
	public Optional<Product> findProductById(UUID id) {
		
		if(id == null)
			throw new IllegalArgumentException("Id null, não é possível realizar a busca.");

		try(Connection conn = DatabaseConnection.getConnection())
		{
			return findById(conn, id);
		}
		catch(SQLException e) {
			throw new PersistenceException("Falha ao consultar produto por ID.", e);
		}
	}
	
	@Override
	public Optional<Product> findById(Connection conn, UUID id) {
		if (conn == null)
		    throw new IllegalArgumentException("A conexão é obrigatória.");
		
		if(id == null)
			throw new IllegalArgumentException("Id null, não é possível realizar a busca.");
		
		String query = """
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
		
		try(PreparedStatement pstm = conn.prepareStatement(query))
		{
			pstm.setString(1, id.toString());

			try(ResultSet rs = pstm.executeQuery()){
				
				if(!rs.next())
					return Optional.empty();
				
				return Optional.of(mapRow(rs));
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Falha ao consultar produto por ID.", e);
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
			throw new PersistenceException("Falha ao consultar produto por código de barras.", e);
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
			throw new PersistenceException("Falha ao verificar existência de produto por código de barras.", e);
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
			throw new PersistenceException("Falha ao verificar duplicidade de código de barras.", e);
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
			
			int affectedRows = pstm.executeUpdate();
			
			if(affectedRows != 1) 
				throw new PersistenceException("A inserção do produto não afetou exatamente uma linha. Linhas afetadas: " + affectedRows);
		}
		catch(SQLException e) {
			throw new PersistenceException("Falha ao inserir produto.", e);
		}
	}

	@Override
	public void update(Product product) {
		if(product == null)
			throw new IllegalArgumentException("O produto não pode ser null.");
		
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
			throw new PersistenceException("Falha ao atualizar produto.", e);
		}
	}

	private Product mapRow(ResultSet rs) throws SQLException {
		try {
			String idValue = rs.getString("id");
			
			if(idValue == null)
				throw new DataMappingException("O ID do produto persistido é null.");
			
			UUID id = UUID.fromString(idValue);
			String name = rs.getString("name");
			String barCode = rs.getString("bar_code");
			String description = rs.getString("description");

			String idCategoryValue = rs.getString("category_id");
			
			if(idCategoryValue == null)
				throw new DataMappingException("O ID da categoria persistida é null.");
			
			UUID categoryId = UUID.fromString(idCategoryValue);
			
			String categoryName = rs.getString("category_name");
			String categoryDescription = rs.getString("category_description");
			Boolean categoryActive = rs.getObject("category_active", Boolean.class);

			ReadOnlyCategory cat = Category.reconstitute(categoryId, categoryName, categoryDescription, categoryActive);

			Integer unitCode = rs.getObject("unit", Integer.class);
			
			UnitOfMeasure unit = unitCode == null ? null : UnitOfMeasure.fromCode(unitCode);

			return Product.reconstitute(id, name, barCode, description, cat, unit);
		}
		catch(IllegalArgumentException e) {
			throw new DataMappingException("Falha ao mapear os dados persistidos para a entidade Product.", e);
		}
	}
}
