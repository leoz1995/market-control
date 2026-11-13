package marketcontrol.service;

import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.repository.CategoryRepository;

public class CategoryService {

	private final CategoryRepository categoryRepository;
	
	public CategoryService(CategoryRepository categoryRepository) {
		if(categoryRepository == null)
			throw new IllegalArgumentException("O repositório de categorias é obrigatório.");
		
		this.categoryRepository = categoryRepository;
	}
	
	public ReadOnlyCategory saveCategory(String name, String description, boolean active){
		Category category = Category.create(name, description, active);
		
		boolean existsName = categoryRepository.existsByName(category.getName());
		
		if(existsName)
			throw new IllegalArgumentException("A categoria "+category.getName()+" já está cadastrada.");
		
		categoryRepository.save(category);
		return category;
	}
	
	public ReadOnlyCategory editCategory(UUID id, String name, String description, boolean active) {
		Category category = categoryRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Categoria inexistente."));
		
		if(categoryRepository.existsByNameAndIdNot(name, id))
			throw new IllegalArgumentException("O nome "+name+" já está em uso.");
		
		category.edit(name, description, active);
		categoryRepository.update(category);
		
		return category;
	}
	
	public ReadOnlyCategory findById(UUID id) {
		if(id == null)
			throw new IllegalArgumentException("O ID da categoria é obrigatório.");
		
		return categoryRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));
	}
	public List<ReadOnlyCategory> getCategories() {
	    return categoryRepository.findAll()
	            .stream()
	            .map(category -> (ReadOnlyCategory) category)
	            .toList();
	}
}
