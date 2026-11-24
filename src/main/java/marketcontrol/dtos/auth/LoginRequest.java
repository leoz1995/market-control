package marketcontrol.dtos.auth;

public record LoginRequest(String email, String password) {
	public LoginRequest{
		if(email == null || email.isBlank())
			throw new IllegalArgumentException("O email é obrigatório.");

		if(password == null || password.isBlank())
			throw new IllegalArgumentException("A senha é obrigatória.");
	}

}
