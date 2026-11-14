package marketcontrol.domain.model.enums;

import java.util.Arrays;

public enum MovementType {
	ENTRY(1, "Entrada"),
	EXIT(2, "Saída"),
	LOSS(3, "Perda"),
	ADJUSTMENT(4, "Ajuste");
	
	private final int code;
	private final String description;
	
	MovementType(int code, String description){
		this.code = code;
		this.description = description;
	}
	
	public static MovementType fromCode(int code) {
		return Arrays.stream(MovementType.values()).filter(c -> c.getCode() == code)
				.findFirst().orElseThrow(() -> new IllegalArgumentException("Código de movimentação não identificado."));
	}
	
	public int getCode() {
		return code;
	}
	
	@Override
	public String toString() {
		return description;
	}
}
