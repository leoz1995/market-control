package marketcontrol;

import java.util.UUID;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;
import marketcontrol.service.CategoryService;
import marketcontrol.service.ProductService;

public class Program {

	public static void main(String[] args) {
		savedProduct();
	}
	
	private static void savedProduct() {
		CategoryService categoryService = new CategoryService();
		ProductService productService = new ProductService(categoryService);
	
		ReadOnlyCategory celulares = categoryService.saveCategory("Celulares", "Eletrônicos", true);
		ReadOnlyCategory computadores = categoryService.saveCategory("Computadores", "Black Friday", true);
		ReadOnlyCategory notebooks = categoryService.saveCategory("HP", "Black Friday", true);

		ReadOnlyProduct saved7 = productService.saveProduct("Iphone 7", "AA1111111111111", "Pro Max Edittion", true, celulares.getId());
	
		productService.getProducts().forEach(System.out::println);
		//UUID search = UUID.randomUUID();
		UUID search2 = saved7.getId();
		ProductDTO dto2 = productService.searchBarCode("AA1111111111111");
		System.out.println("Encontrado: "+dto2.toString());
		ProductDTO newProduct = new ProductDTO("Xiaomi 10", "AA1111111111111", "New Generation 5.0", false, celulares.getId());

		try 
		{
			categoryService.editCategory(celulares.getId(), "Celulares", "Black Fridday", true);
			ReadOnlyProduct editted = productService.editProduct(search2, newProduct);

			productService.getProducts().forEach(System.out::println);

			ProductDTO dto = productService.searchBarCode("AA1111111111111");
			System.out.println("Encontrado: "+dto.toString());
			System.out.println(categoryService.findById(saved7.getCategory().getId()));
			System.out.println(categoryService.getCategories());
		}
		catch (IllegalArgumentException exception) {
			System.out.println("Não foi possível cadastrar: "+exception.getMessage());
		}
	}
}
