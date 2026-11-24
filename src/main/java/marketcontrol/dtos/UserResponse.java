package marketcontrol.dtos;

import java.time.LocalDateTime;
import java.util.UUID;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.exception.data.DataMappingException;

public record UserResponse(UUID id, String name, String email, LocalDateTime updateTime, LocalDateTime entryDate, Role type) {

	public UserResponse{
		if(id == null)
			throw new DataMappingException("ID inválido na reconstrução do usuário.");

		if(name == null || name.isBlank())
			throw new DataMappingException("Nome inválido na reconstrução do usuário.");

		if(email == null || email.isBlank())
			throw new DataMappingException("Email inválido na reconstrução do usuário.");

		//if(updateTime == null)
			//throw new IllegalArgumentException("Última atualização inválida na reconstrução do usuário.");

		if(entryDate == null)
			throw new DataMappingException("Data de entrada inválida na reconstrução do usuário.");

		if(type == null)
			throw new DataMappingException("Tipo de hierarquia inválido na reconstrução do usuário.");
	}
}
