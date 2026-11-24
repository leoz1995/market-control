package marketcontrol.dtos.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.exception.data.DataMappingException;

public record UserAuth(UUID id, String name, String email, String passwordHash, LocalDateTime updateTime, LocalDateTime entryDate, Role type) {

	public UserAuth{
		if(type == null)
			throw new DataMappingException("O tipo de hierarquia do usuário é inválido.");
	}
}
