package marketcontrol.domain.model;

import java.util.UUID;

public class Product implements ReadOnlyProduct{
	//**Atributos: {id, name, barcode, description, category, minimumStock, unitOfMeasure, active}**
	private final UUID id;
	private String name;
	private String barCode;
	private String description;
	private boolean active;
	
	private Product(String name, String barCode, String description, boolean active) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.barCode = barCode;
		this.description = description;
		this.active = active;
	}

	public static Product create(String name, String barCode, String description, boolean active) {
		if(name == null)
			throw new IllegalArgumentException("O nome é obrigatório");

		String cleanName = name.trim();
		if(cleanName.isBlank())
			throw new IllegalArgumentException("O nome é obrigatório");

		if(barCode == null)
			throw new IllegalArgumentException("O código de barras não pode ser nullo.");
		
		String cleanBarCode = barCode.trim();
		if(cleanBarCode.isBlank())
			throw new IllegalArgumentException("O código de barras é obrigatório.");
		
		String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();

		return new Product(cleanName, cleanBarCode, cleanDescription, active);
	}
	
	public void edit(String name, String barCode, String description, boolean active) {		
		if(name == null || name.isBlank())
			throw new IllegalArgumentException("O nome não pode ser nullo.");
		
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("O código de barras é obrigatório.");
		
		String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();
		
		this.name = name.trim();
		this.barCode = barCode.trim();
		this.description = cleanDescription;
		this.active = active;
	}
	
	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getBarCode() {
		return barCode;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}

	@Override
	public String toString() {
		return "Product: [ID: "+id+" | " + name + ", barCode=" + barCode + ", description=" + description + ", active=" + active
				+ "]";
	}
}
