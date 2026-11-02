package marketcontrol;

import java.util.UUID;

import marketcontrol.domain.model.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;
import marketcontrol.service.ProductService;

public class Program {

	private static final ProductService productService = new ProductService();
	
	public static void main(String[] args) {
		savedProduct();
	}
	
	private static void savedProduct() {
		try {
			ReadOnlyProduct saved = productService.saveProduct("Iphone 17", "A1264897S302027", "Pro Max Edittion", true);
			ReadOnlyProduct saved1 = productService.saveProduct("Iphone 18", "A1264897S332027", "Pro Max Edittion", true);
			ReadOnlyProduct saved2 = productService.saveProduct("Iphone 14", "A1264897S342027", "Pro Max Edittion", true);
			ReadOnlyProduct saved3 = productService.saveProduct("Iphone 13", "A1264897S352027", "Pro Max Edittion", true);
			ReadOnlyProduct saved4 = productService.saveProduct("Iphone 12", "A1264897S362027", "Pro Max Edittion", true);
			ReadOnlyProduct saved5 = productService.saveProduct("Iphone 11", "A1264897S372027", "Pro Max Edittion", true);
			ReadOnlyProduct saved6 = productService.saveProduct("Iphone 15", "A1264897S382027", "", true);
			ReadOnlyProduct saved7 = productService.saveProduct("Iphone 7", "AA1111111111111", "Pro Max Edittion", true);
			
			productService.getProducts().forEach(System.out::println);
			//UUID search = UUID.randomUUID();
			UUID search2 = saved7.getId();

			ProductDTO newProduct = new ProductDTO("Xiaomi 10", "AA1111111111111", "New Generation 5.0", false);
			ReadOnlyProduct editted = productService.editProduct(search2, newProduct);

			productService.getProducts().forEach(System.out::println);

			ProductDTO dto = productService.searchBarCode("AA1111111111111");
			System.out.println("Encontrado: "+dto.toString());
		}
		catch (IllegalArgumentException exception) {
			System.out.println("Não foi possível cadastrar: "+exception.getMessage());
		}
	}
}
