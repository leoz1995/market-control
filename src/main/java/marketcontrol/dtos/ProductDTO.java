package marketcontrol.dtos;

import java.util.UUID;

public record ProductDTO(String name, String barCode, String description, UUID categoryId) {

	@Override
	public String toString() {
		return "CodeBar: "+barCode+" | Name: "+ name + ", Description:"+ description + " | Category: "+String.valueOf(categoryId);
	}
}
