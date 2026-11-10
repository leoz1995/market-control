package marketcontrol.domain.model.enums;

public enum UnitOfMeasure {
	UNIT(1, "Unidade"), 
	KILOGRAM(2, "Quilo"), 
	GRAM(3, "Gramas"), 
	LITER(4, "Litro"), 
	MILLILITER(5, "Mililitros");
	
	private final int code;
	private final String description;
	
	UnitOfMeasure(int code, String description){
		this.code = code;
		this.description = description;
	}
	
	public int getCode() {
		return code;
	}
	
	public String getDescription() {
		return description;
	}
	
	@Override
	public String toString() {
		return description;
	}
}
