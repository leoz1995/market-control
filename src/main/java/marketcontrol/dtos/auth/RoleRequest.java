package marketcontrol.dtos.auth;

import marketcontrol.domain.model.enums.Role;

public record RoleRequest(Role role) {
    public RoleRequest {
        if (role == null) throw new IllegalArgumentException("O papel é obrigatório.");
    }
}
