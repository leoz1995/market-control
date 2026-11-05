package marketcontrol.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;

public class ProductService {
	private final Map<UUID, Product> products = new LinkedHashMap<>();
	private final Map<String, UUID> barCodeIndex = new HashMap<>();
	
	private final CategoryService categoryService;
	
	public ProductService(CategoryService categoryService) {
		this.categoryService = categoryService;
	}
	
	public ReadOnlyProduct saveProduct(String name, String barCode, String description, boolean active, UUID categoryId) {
		Category category = categoryService.findById(categoryId);
		Product product = Product.create(name, barCode, description, active, category);
				
		barCodeCheck(product.getBarCode().trim().toUpperCase(Locale.ROOT) ,product.getId());
		
		products.put(product.getId(), product);
		barCodeIndex.put(product.getBarCode().trim().toUpperCase(Locale.ROOT), product.getId());
		
		return product;
	}
	
	public ReadOnlyProduct editProduct(UUID uuid, ProductDTO dto) {
		if(dto == null)
			throw new IllegalArgumentException("Produto não foi identificado.");

		Product product = products.get(uuid);
		
		if(product == null)
			throw new IllegalArgumentException("Produto com UUID informado não foi encontrado.");
		
		if(dto.barCode() == null)
			throw new IllegalArgumentException("Código de barra é nullo.");
		
		String oldBarCode = product.getBarCode();
		String newBarCode = dto.barCode().toUpperCase(Locale.ROOT).trim();

		if(newBarCode.isBlank())
			throw new IllegalArgumentException("Código de barras inválido.");

		barCodeCheck(newBarCode, uuid);

		Category currentCategory = categoryService.findById(dto.categoryId());
	
		product.edit(dto.name(), newBarCode, dto.description(), dto.active(), currentCategory);
		barCodeApply(newBarCode, oldBarCode, uuid);
		
		return product;
	}
	
	public ProductDTO searchBarCode(String barCode) 
	{
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("O código de barras é obrigatório.");
		
		return Optional.ofNullable(barCodeIndex.get(barCode.toUpperCase(Locale.ROOT).trim()))
				.map(products::get)
				.map(product -> new ProductDTO(product.getName(), product.getBarCode(), product.getDescription(), product.isActive(), product.getCategory().getId()))
				.orElseThrow(() -> new IllegalArgumentException("Esse código de barras não existe."));
	}
	
	private void barCodeCheck(String newBarCode, UUID uuid) {
		UUID existingId = barCodeIndex.get(newBarCode);
		if (existingId != null && !existingId.equals(uuid))
			throw new IllegalArgumentException("Já existe outro produto com esse código cadastrado.");
	}
	
	private void barCodeApply(String newBarCode, String oldBarCode, UUID uuid) {
		if (!oldBarCode.equals(newBarCode)) {
			barCodeIndex.remove(oldBarCode);
			barCodeIndex.put(newBarCode, uuid);
		}
	}
	
	public Product searchById(UUID productId) {
		Product product = products.get(productId);
		
		if(product == null)
			throw new IllegalArgumentException("O produto buscado não existe.");
		
		return product;
	}
	
	public List<ReadOnlyProduct> getProducts(){
		return List.copyOf(products.values());
	}
}
