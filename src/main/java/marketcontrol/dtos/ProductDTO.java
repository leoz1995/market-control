package marketcontrol.dtos;

public record ProductDTO(String name, String barCode, String description, boolean active) {

	@Override
	public String toString() {
		return "CodeBar: "+barCode+" | Name: "+ name + ", Description:"+ description + ", active: "+ active;
	}
}
