package marketcontrol.domain.model;

import java.util.Locale;
import java.util.UUID;

import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;

public class Product implements ReadOnlyProduct{
	private final UUID id;
	private String name;
	private String barCode;
	private String description;
	private Category category;
	private final UnitOfMeasure unit;
	
	private Product(String name, String barCode, String description, Category category, UnitOfMeasure unit) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.barCode = barCode.trim().toUpperCase(Locale.ROOT);
		this.description = description;
		this.category = category;
		this.unit = unit;
	}

	public static Product create(String name, String barCode, String description, Category category, UnitOfMeasure unit) {
		if(category == null)
			throw new IllegalArgumentException("A categoria é obrigatória.");
		
		if(!category.isActive())
			throw new IllegalArgumentException("A categoria precisa ser ativa.");
		
		if(name == null)
			throw new IllegalArgumentException("O nome é obrigatório");

		if(unit == null)
			throw new IllegalArgumentException("O tipo de medida é obrigatório.");
		
		String cleanName = name.trim();
		if(cleanName.isBlank())
			throw new IllegalArgumentException("O nome é obrigatório");

		if(barCode == null)
			throw new IllegalArgumentException("O código de barras não pode ser nullo.");
		
		if(barCode.trim().isBlank())
			throw new IllegalArgumentException("O código de barras é obrigatório.");
		
		String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();

		return new Product(cleanName, barCode, cleanDescription, category, unit);
	}
	
	public void edit(String name, String barCode, String description, Category category) {
		
		if(name == null)
			throw new IllegalArgumentException("O nome não pode ser nullo.");

		String cleanName = name.trim();
		if(cleanName.isBlank())
			throw new IllegalArgumentException("O nome não pode ser nullo.");

		Category currentCategory = this.category;
		
		if(currentCategory == null || category == null)
			throw new IllegalArgumentException("Categoria indisponível.");
		
		boolean sameCategory = currentCategory.getId()
		    .equals(category.getId());
		
		if (!sameCategory && !category.isActive()) {
		    throw new IllegalArgumentException(
		        "A nova categoria precisa estar ativa."
		    );
		}
		
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("O código de barras é obrigatório.");

		String cleanDescription = (description == null || description.isBlank()) ? null : description.trim();

		this.name = name.trim();
		this.barCode = barCode.trim().toUpperCase(Locale.ROOT);
		this.description = cleanDescription;
		this.category = category;
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

	public ReadOnlyCategory getCategory() {
		return category;
	}

	public UnitOfMeasure getUnit() {
		return unit;
	}
	
	@Override
	public String toString() {
		return "Product: [ID: "+id+" | " + name + ", barCode=" + barCode + ", description=" + description + " | "+getCategory()+" | "+unit+" ]";
	}
}
