package marketcontrol.service;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.UserRepository;
import marketcontrol.dtos.UserResponse;
import marketcontrol.dtos.auth.LoginResponse;
import marketcontrol.dtos.auth.UserAuth;
import marketcontrol.exception.AuthenticationException;
import marketcontrol.exception.InactiveAccountException;
import marketcontrol.security.SecurityPassword;

public class AuthService {

	private final UserRepository userRepository;
	private final SecurityPassword securityPassword;
	private final SessionService sessionService;

	public AuthService(UserRepository userRepository, SecurityPassword securityPassword, SessionService sessionService){
		this.userRepository = userRepository;
		this.securityPassword = securityPassword;
		this.sessionService = sessionService;
	}

	public SessionService sessionService() { return sessionService; }

	public LoginResponse authenticate(String email, String password) {

		if(email == null || email.isBlank())
			throw new IllegalArgumentException("Email inválido, preencha corretamente.");

		if(password == null || password.isBlank())
			throw new IllegalArgumentException("Insira a senha do usuário.");

		String emailValue = email.trim().toUpperCase(java.util.Locale.ROOT);
		UserAuth u = userRepository.authUser(emailValue)
				.orElseThrow(AuthenticationException::new);

		if (!securityPassword.matches(password, u.passwordHash()))
			throw new AuthenticationException();

		if (u.type() == Role.INACTIVE)
			throw new InactiveAccountException();

		UserResponse uResponse = new UserResponse(u.id(), u.name(), u.email(), u.updateTime(), u.entryDate(), u.type());
		var session = sessionService.create(uResponse.id(), uResponse.type());
		return new LoginResponse(session.accessToken(), session.refreshToken(), session.expiresInSeconds(), "Bearer", uResponse);
	}

	public LoginResponse refresh(String refreshToken) {
		var userId = sessionService.userIdForRefresh(refreshToken);
		UserAuth u = userRepository.findById(userId).orElseThrow(AuthenticationException::new);
		var session = sessionService.refresh(refreshToken);
		UserResponse response = new UserResponse(u.id(), u.name(), u.email(), u.updateTime(), u.entryDate(), session.role());
		return new LoginResponse(session.accessToken(), session.refreshToken(), session.expiresInSeconds(), "Bearer", response);
	}

}
