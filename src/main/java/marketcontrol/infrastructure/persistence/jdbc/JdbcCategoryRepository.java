package marketcontrol.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.repository.CategoryRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;

public class JdbcCategoryRepository implements CategoryRepository{
	
	@Override
	public List<Category> findAll() {
		String sql = "SELECT id, name, description, active FROM categories";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql);
				ResultSet rs = pstm.executeQuery()){

			List<Category> categories = new ArrayList<>();
			while(rs.next()) {
				UUID id = UUID.fromString(rs.getString("id"));
				String name = rs.getString("name");
				String description = rs.getString("description");
				boolean active = rs.getBoolean("active");
				
				Category cat = Category.reconstitute(id, name, description, active);
				categories.add(cat);
			}
			return categories;
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca de categorias.", e);
		}
	}

	@Override
	public boolean existsById(UUID id){
		
		if(id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");
		
		String sql = "SELECT 1 FROM categories WHERE id = ?";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			
			pstm.setString(1, id.toString());
			try(ResultSet rs = pstm.executeQuery()){
				return rs.next();
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo ID.", e);
		}
	}

	@Override
	public boolean existsByName(String name){
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome é obrigatório.");
		
		String sql = "SELECT 1 from categories WHERE name = ?";
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			
			String nameValue = name.trim();
			
			pstm.setString(1, nameValue);
			try(ResultSet rs = pstm.executeQuery()){		
				return rs.next();
			}
		}catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo nome.", e);
		}
	}
	
	@Override
	public void save(Category category){
		if(category == null)
			throw new IllegalArgumentException("A categoria não pode ser nulla.");
		
		String sql = "INSERT INTO categories (id, name, description, active) VALUES (?, ?, ?, ?)";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			
			pstm.setString(1, category.getId().toString());
			pstm.setString(2, category.getName());
			pstm.setString(3, category.getDescription());
			pstm.setBoolean(4, category.isActive());
			
			pstm.executeUpdate();
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível salvar essa categoria.", e);
		}
	}

	@Override
	public Optional<Category> findById(UUID id) {
		if(id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");
		
		String sql = "SELECT id, name, description, active FROM categories WHERE id = ?";
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){
			
			pstm.setString(1, id.toString());
			try(ResultSet rs = pstm.executeQuery()){	
				
				if(!rs.next())
					return Optional.empty();
				
				UUID uuid = UUID.fromString(rs.getString("id"));
				String name = rs.getString("name");
				String description = rs.getString("description");
				boolean active = rs.getBoolean("active");
				
				Category cat = Category.reconstitute(uuid, name, description, active);
				
				return Optional.of(cat);
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível realizar a busca pelo nome.", e);
		}
	}

	@Override
	public boolean existsByNameAndIdNot(String name, UUID id) {
		if(id == null)
			throw new IllegalArgumentException("O ID é obrigatório.");
		
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome é obrigatório.");
		
		String sql = "SELECT 1 FROM categories WHERE name = ? AND id <> ?";
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){

			pstm.setString(1, name.trim());
			pstm.setString(2, id.toString());
			try(ResultSet rs = pstm.executeQuery()){	
				return rs.next();
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível verificar a existência de outra categoria com esse ID.", e);
		}
	}

	@Override
	public void update(Category category) {
		if(category == null)
			throw new IllegalArgumentException("A categoria nulla não pode ser atualizada. Preencha as informações corretamente!");
		
		String sql = "UPDATE categories SET name = ?, description = ?, active = ? WHERE id = ?";
		
		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){

			pstm.setString(1, category.getName());
			pstm.setString(2, category.getDescription());
			pstm.setBoolean(3, category.isActive());
			pstm.setString(4, category.getId().toString());

			pstm.executeUpdate();
			
		}
		catch(SQLException e) {
			throw new PersistenceException("Não foi possível atualizar a categoria.", e);
		}
		
	}

}
