package marketcontrol.dtos;

import java.util.UUID;

import marketcontrol.domain.model.enums.UnitOfMeasure;

public record ProductDTO(String name, String barCode, String description, UUID categoryId, UnitOfMeasure unit) {

	@Override
	public String toString() {
		return "CodeBar: "+barCode+" | Name: "+ name + ", Description:"+ description +" | Unit Type: "+ unit + " | Category: "+categoryId;
	}
}
