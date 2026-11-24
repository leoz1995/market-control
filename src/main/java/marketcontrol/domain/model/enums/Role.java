package marketcontrol.domain.model.enums;

import java.util.Arrays;

public enum Role {
	INACTIVE(1, "Inactive"),
	ADMIN(2, "Admin"),
	STOCK_OPERATOR(3, "Stocker");

	private final int code;
	private final String description;

	Role(int code, String description){
		this.code = code;
		this.description = description;
	}

	public static Role fromCode(int code) {
		return Arrays.stream(Role.values()).filter(c -> c.getCode() == code)
				.findFirst().orElseThrow(() -> new IllegalArgumentException("Código de autorização não identificado."));
	}

	public int getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}
}
