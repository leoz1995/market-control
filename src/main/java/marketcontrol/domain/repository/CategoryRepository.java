package marketcontrol.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Category;

public interface CategoryRepository {

	List<Category> findAll();
	
	Optional<Category> findById(UUID id);
	
	boolean existsById(UUID id);
	
	boolean existsByName(String name);
	
	boolean existsByNameAndIdNot(String name, UUID id);
	
	void save(Category category);
	
	void update(Category category);
}
