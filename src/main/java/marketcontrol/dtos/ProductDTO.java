package marketcontrol.dtos;

import java.util.UUID;

public record ProductDTO(String name, String barCode, String description, boolean active, UUID categoryId) {

	@Override
	public String toString() {
		return "CodeBar: "+barCode+" | Name: "+ name + ", Description:"+ description + ", active: "+ active + " | Category: "+String.valueOf(categoryId);
	}
}
