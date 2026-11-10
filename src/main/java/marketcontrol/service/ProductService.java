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
		
		if(categoryService == null)
			throw new IllegalArgumentException("Serviço de categoria não instanciado corretamente.");
		
		this.categoryService = categoryService;
	}
	
	public ReadOnlyProduct saveProduct(String name, String barCode, String description, UUID categoryId) {
		Category category = categoryService.findById(categoryId);
		Product product = Product.create(name, barCode, description, category);
		
		String normalizedBarCode = normalizeBarCode(product.getBarCode());
		barCodeCheck(normalizedBarCode, product.getId());
		
		products.put(product.getId(), product);
		barCodeIndex.put(normalizedBarCode, product.getId());
		
		return product;
	}
	
	public ReadOnlyProduct editProduct(UUID uuid, ProductDTO dto) {
		if(uuid == null)
			throw new IllegalArgumentException("ID inválido.");
		
		if(dto == null)
			throw new IllegalArgumentException("Produto não foi identificado.");

		Product product = products.get(uuid);
		
		if(product == null)
			throw new IllegalArgumentException("Produto com UUID informado não foi encontrado.");
		
		if(dto.barCode() == null)
			throw new IllegalArgumentException("Código de barra é nullo.");
		
		String oldBarCode = product.getBarCode();
		String normalizedBarCode = normalizeBarCode(dto.barCode());
		
		barCodeCheck(normalizedBarCode, uuid);

		Category currentCategory = categoryService.findById(dto.categoryId());
	
		product.edit(dto.name(), normalizedBarCode, dto.description(), currentCategory);
		barCodeApply(normalizedBarCode, oldBarCode, uuid);
		
		return product;
	}
	
	public ProductDTO searchBarCode(String barCode) 
	{
		String normalizedBarCode = normalizeBarCode(barCode);
		
		return Optional.ofNullable(barCodeIndex.get(normalizedBarCode))
				.map(products::get)
				.map(product -> new ProductDTO(product.getName(), product.getBarCode(), product.getDescription(), product.getCategory().getId()))
				.orElseThrow(() -> new IllegalArgumentException("Esse código de barras não existe."));
	}
	
	public ReadOnlyProduct searchProduct(String barCode) {
		String normalizedBarCode = normalizeBarCode(barCode);
		
		UUID p = barCodeIndex.get(normalizedBarCode);
		ReadOnlyProduct product = products.get(p);
		return Optional.ofNullable(product).orElseThrow(() -> new IllegalArgumentException("Código de barras inválido."));
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
	
	Product searchById(UUID productId) {
		Product product = products.get(productId);
		
		if(product == null)
			throw new IllegalArgumentException("O produto buscado não existe.");
		
		return product;
	}
	
	private String normalizeBarCode(String barCode) {
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("Código de barras inválido.");
		
		return barCode.trim().toUpperCase(Locale.ROOT);
	}
	
	public List<ReadOnlyProduct> getProducts(){
		return List.copyOf(products.values());
	}
}
