package marketcontrol.service;

import java.nio.charset.StandardCharsets;

import marketcontrol.domain.model.User;
import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.UserRepository;
import marketcontrol.dtos.UserResponse;
import marketcontrol.dtos.auth.UserAuth;
import marketcontrol.exception.ResourceNotFoundException;
import marketcontrol.exception.ConflictException;
import marketcontrol.security.SecurityPassword;

public class UserService {

	private final SecurityPassword securityPassword;
	private final UserRepository userRepository;

	public UserService(SecurityPassword securityPassword, UserRepository userRepository){
		this.securityPassword = securityPassword;
		this.userRepository = userRepository;
	}

	public UserResponse register(String name, String email, String password){
		return register(name, email, password, Role.INACTIVE);
	}

	public UserResponse register(String name, String email, String password, Role role){
		if(password == null || password.isBlank())
			throw new IllegalArgumentException("A senha da conta é obrigatório.");

		if(password.getBytes(StandardCharsets.UTF_8).length > 72 )
			throw new IllegalArgumentException("A senha não pode ultrapassar 72 bytes.");

		String normalizedEmail = email == null ? null : email.trim().toUpperCase(java.util.Locale.ROOT);
		boolean emailExists = userRepository.userEmailExists(normalizedEmail);

		if(emailExists) throw new ConflictException("Já existe uma conta com esse email.");

		String hash = securityPassword.encode(password);
		User user = User.create(name, normalizedEmail, hash, role);
		userRepository.save(user);

		UserResponse u = new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getUpdateInfo(), user.getEntryDate(), user.getType());

		return u;
	}

	public java.util.List<UserResponse> listUsers() {
		return userRepository.findAll().stream().map(UserService::response).toList();
	}

	public UserResponse changeRole(java.util.UUID id, Role role) {
		if (role == null) throw new IllegalArgumentException("O papel informado é inválido.");
		UserAuth current = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
		if (current.type() == role) return response(current);
		userRepository.updateRole(id, role);
		return userRepository.findById(id).map(UserService::response)
				.orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
	}

	public UserResponse activate(java.util.UUID id, Role role) {
		if (role != Role.ADMIN && role != Role.STOCK_OPERATOR)
			throw new IllegalArgumentException("Informe ADMIN ou STOCK_OPERATOR para ativar a conta.");
		return changeRole(id, role);
	}

	private static UserResponse response(UserAuth user) {
		return new UserResponse(user.id(), user.name(), user.email(), user.updateTime(), user.entryDate(), user.type());
	}
}
