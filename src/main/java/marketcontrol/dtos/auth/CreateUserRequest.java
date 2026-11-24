package marketcontrol.dtos.auth;

import marketcontrol.domain.model.enums.Role;

public record CreateUserRequest(String name, String email, String password, Role role) {
    public CreateUserRequest {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("O nome é obrigatório.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("O email é obrigatório.");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("A senha é obrigatória.");
    }
}
