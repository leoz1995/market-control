package marketcontrol.domain.model;

import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;

public class Category implements ReadOnlyCategory{
	private final UUID id;
	private String name;
	private String description;
	private boolean active;
	
	private Category(String name, String description, boolean active) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.description = description;
		this.active = active;
	}

	public static Category create(String name, String description, boolean active) {
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome de categoria é obrigatório");
				
		String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();
		
		return new Category(name.trim(), cleanDescription, active);
	}
	
	public void edit(String name, String description, boolean active) {
		
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome de categoria é obrigatório.");
		
		String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();
		
		this.name = name.trim();
		this.description = cleanDescription;
		this.active = active;
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
