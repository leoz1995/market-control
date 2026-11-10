package marketcontrol.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;

public class CategoryService {

	private final Map<UUID, Category> categories = new LinkedHashMap<>();
	
	public ReadOnlyCategory saveCategory(String name, String description, boolean active) {
		Category category = Category.create(name, description, active);
		
		boolean existsName = categories.values().stream().anyMatch(cat -> name.trim().equalsIgnoreCase(cat.getName().trim()));
		
		if(existsName)
			throw new IllegalArgumentException("A categoria "+name+" já está cadastrada.");
		
		categories.put(category.getId(), category);
		return category;
	}
	
	public ReadOnlyCategory editCategory(UUID id, String name, String description, boolean active) {
		Category category = categories.get(id);
		
		if(category == null)
			throw new IllegalArgumentException("Categoria inexistente.");
		
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome é obrigatório.");
		
		String nameValid = name.trim();
		boolean existsName = categories.values().stream().filter(my -> !my.getId().equals(category.getId())).anyMatch(cat -> nameValid.equalsIgnoreCase(cat.getName()));
		
		if(existsName)
			throw new IllegalArgumentException("A categoria "+nameValid+" já está em uso.");
		
		category.edit(nameValid, description, active);
		
		return category;
	}
	
	Category findById(UUID id) {
		if(id == null)
			throw new IllegalArgumentException("ID da categoria inválido.");
		
		Category category = categories.get(id);	
		
		if(category == null)
			throw new IllegalArgumentException("Categoria não encontrada.");
		
		return category;
	}
	
	public List<ReadOnlyCategory> getCategories(){
		return List.copyOf(categories.values());
	}
}
