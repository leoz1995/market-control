package marketcontrol.domain.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.User;
import marketcontrol.dtos.auth.UserAuth;

public interface UserRepository {

	void save(User u);
	Optional<UserAuth> authUser(String email);
	boolean userEmailExists(String email);
	List<UserAuth> findAll();
	Optional<UserAuth> findById(UUID id);
	void updateRole(UUID id, marketcontrol.domain.model.enums.Role role);
}
