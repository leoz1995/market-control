package marketcontrol.domain.model;

import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.exception.data.DataMappingException;

public class Category implements ReadOnlyCategory{
	private final UUID id;
	private String name;
	private String description;
	private boolean active;
	
	private Category(UUID id, String name, String description, boolean active) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.active = active;
	}

	public static Category create(String name, String description, boolean active) {
		validateName(name);
		
		if(description == null || description.isBlank())
			throw new IllegalArgumentException("A descrição da categoria é obrigatório.");
		
		return new Category(UUID.randomUUID(), name.trim(), description.trim(), active);
	}
	
	public void edit(String name, String description, boolean active) {
		validateName(name);
		
		String descriptionValid;
		if(description == null || description.isBlank())
			descriptionValid = getDescription();
		else 
			descriptionValid = description.trim();
		
		this.name = name.trim();
		this.description = descriptionValid;
		this.active = active;
	}

	public static Category reconstitute(UUID id, String name, String description, boolean active) {
		if(id == null)
			throw new DataMappingException("O ID da categoria persistida é obrigatório.");
		
		if(name == null || name.isBlank())
			throw new DataMappingException("O nome da categoria persistida é obrigatório.");

		if(description == null || description.isBlank())
			throw new DataMappingException("A descrição da categoria persistida é obrigatório.");
		
		return new Category(id, name.trim(), description.trim(), active);
	}
	
	private static void validateName(String name) {
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome da categoria é obrigatório.");
	}
	
	public UUID getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}

	@Override
	public String toString() {
		return "Category [id=" + id + ", name=" + name + ", description=" + description + ", active=" + active + "]";
	}

}
