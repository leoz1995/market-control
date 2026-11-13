package marketcontrol.domain.model;

import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;

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
				
		String cleanDescription = cleanDescription(description);
		
		return new Category(UUID.randomUUID(), name.trim(), cleanDescription, active);
	}
	
	public void edit(String name, String description, boolean active) {
		validateName(name);
		
		String cleanDescription = cleanDescription(description);
		
		this.name = name.trim();
		this.description = cleanDescription;
		this.active = active;
	}

	public static Category reconstitute(UUID id, String name, String description, boolean active) {
		if(id == null)
			throw new IllegalArgumentException("O ID da categoria é obrigatório.");
		
		validateName(name);
		
		String cleanDescription = cleanDescription(description);
		
		return new Category(id, name.trim(), cleanDescription, active);
	}
	
	private static void validateName(String name) {
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome da categoria é obrigatório.");
	}
	
	private static String cleanDescription(String description) {
		return (description == null || description.isBlank()) ? null : description.trim();
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
