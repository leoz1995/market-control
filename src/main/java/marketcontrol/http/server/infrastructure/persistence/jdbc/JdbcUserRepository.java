package marketcontrol.http.server.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.User;
import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.UserRepository;
import marketcontrol.dtos.auth.UserAuth;
import marketcontrol.exception.PersistenceException;
import marketcontrol.exception.data.DataMappingException;
import marketcontrol.http.server.infrastructure.database.DatabaseConnection;

public class JdbcUserRepository implements UserRepository{
	private static final String USER_COLUMNS = "id, name, email, password, update_time, entry_time, role";

	@Override
	public void save(User u) {
        if(u == null)
            throw new IllegalArgumentException("O Usuário não pode ser null.");

        String sql = "INSERT INTO users (id, name, email, password, update_time, entry_time, role) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try(Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstm = conn.prepareStatement(sql)){

            pstm.setString(1, u.getId().toString());
            pstm.setString(2, u.getName());
            pstm.setString(3, u.getEmail());
            pstm.setString(4, u.getPassword());
            pstm.setObject(5, u.getUpdateInfo());
            pstm.setObject(6, u.getEntryDate());
            pstm.setInt(7, u.getType().getCode());

            pstm.executeUpdate();
        }
		catch(SQLException e) {
			if ("23000".equals(e.getSQLState()))
				throw new marketcontrol.exception.ConflictException("Já existe uma conta com esse email.");
            throw new PersistenceException("Não foi possível salvar esse Usuário.", e);
        }
	}

	@Override
	public boolean userEmailExists(String email) {
		if (email == null || email.isBlank())
			throw new IllegalArgumentException("O email é obrigatório.");

		String sql = "SELECT 1 FROM users WHERE email = ?";

		try (Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)) {
			pstm.setString(1, email.trim().toUpperCase(Locale.ROOT));
			try (ResultSet rs = pstm.executeQuery()) {
				return rs.next();
			}

		} catch (SQLException e) {
			throw new PersistenceException("Falha ao verificar existência de email do usuário.", e);
		}
	}

	@Override
	public Optional<UserAuth> authUser(String email) {
		if(email == null || email.isBlank())
			throw new IllegalArgumentException("O email é inválido.");

		String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE email = ?";

		try(Connection conn = DatabaseConnection.getConnection();
				PreparedStatement pstm = conn.prepareStatement(sql)){

			pstm.setString(1, email.trim().toUpperCase(Locale.ROOT));

			try(ResultSet rs = pstm.executeQuery()){
				if(!rs.next())
					return Optional.empty();

				return Optional.of(mapUser(rs));
			}

		}catch(SQLException e) {
			throw new PersistenceException("Falha ao verificar existência do usuário por email.", e);
		}
	}

	@Override
	public List<UserAuth> findAll() {
		String sql = "SELECT " + USER_COLUMNS + " FROM users ORDER BY name";
		try (Connection c = DatabaseConnection.getConnection(); var p = c.prepareStatement(sql); var r = p.executeQuery()) {
			List<UserAuth> users = new ArrayList<>();
			while (r.next()) users.add(mapUser(r));
			return users;
		} catch (SQLException e) { throw new PersistenceException("Falha ao listar usuários.", e); }
	}

	@Override
	public Optional<UserAuth> findById(UUID id) {
		String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE id = ?";
		try (Connection c = DatabaseConnection.getConnection(); var p = c.prepareStatement(sql)) {
			p.setString(1, id.toString());
			try (var r = p.executeQuery()) { return r.next() ? Optional.of(mapUser(r)) : Optional.empty(); }
		} catch (SQLException e) { throw new PersistenceException("Falha ao consultar usuário.", e); }
	}

	@Override
	public void updateRole(UUID id, Role role) {
		String sql = "UPDATE users SET role = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?";
		try (Connection c = DatabaseConnection.getConnection()) {
			c.setAutoCommit(false);
			Role previousRole;
			try (var p = c.prepareStatement("SELECT role FROM users WHERE id = ? FOR UPDATE")) {
				p.setString(1, id.toString());
				try (var r = p.executeQuery()) {
					if (!r.next()) throw new marketcontrol.exception.ResourceNotFoundException("Usuário não encontrado.");
					previousRole = Role.fromCode(r.getInt(1));
				}
			}
			if (previousRole == Role.ADMIN && role != Role.ADMIN) {
				long administrators = 0;
				try (var p = c.prepareStatement("SELECT id FROM users WHERE role = ? FOR UPDATE")) {
					p.setInt(1, Role.ADMIN.getCode());
					try (var r = p.executeQuery()) { while (r.next()) administrators++; }
				}
				if (administrators <= 1)
					throw new marketcontrol.exception.ConflictException("O último administrador ativo não pode ser desativado ou rebaixado.");
			}
			try (var p = c.prepareStatement(sql)) {
				p.setInt(1, role.getCode()); p.setString(2, id.toString());
				if (p.executeUpdate() == 0) throw new marketcontrol.exception.ResourceNotFoundException("Usuário não encontrado.");
			}
			try (var p = c.prepareStatement("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL")) {
				p.setString(1, id.toString()); p.executeUpdate();
			}
			c.commit();
		} catch (SQLException e) { throw new PersistenceException("Falha ao atualizar papel e revogar sessões.", e); }
	}

	private static UserAuth mapUser(ResultSet rs) throws SQLException {
		String id = rs.getString("id");
		if (id == null) throw new DataMappingException("O ID do usuário persistido é null.");
		Integer roleCode = rs.getObject("role", Integer.class);
		return new UserAuth(UUID.fromString(id), rs.getString("name"), rs.getString("email"),
				rs.getString("password"), rs.getObject("update_time", LocalDateTime.class),
				rs.getObject("entry_time", LocalDateTime.class), roleCode == null ? null : Role.fromCode(roleCode));
	}

}
