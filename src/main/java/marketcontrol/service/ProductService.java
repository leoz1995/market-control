package marketcontrol.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;

public class ProductService {
	private final Map<UUID, Product> products = new LinkedHashMap<>();
	private final Map<String, UUID> barCodeIndex = new HashMap<>();
	
	public ReadOnlyProduct saveProduct(String name, String barCode, String description, boolean active) {
		Product product = Product.create(name, barCode, description, active);
		
		if(barCodeIndex.containsKey(product.getBarCode()))
	        throw new IllegalArgumentException("Já existe um produto cadastrado com este código de barras.");
			
		products.put(product.getId(), product);
		barCodeIndex.put(product.getBarCode(), product.getId());
		
		return product;
	}
	
	public ReadOnlyProduct editProduct(UUID uuid, ProductDTO dto) {
		Product product = products.get(uuid);

		if(product == null)
			throw new IllegalArgumentException("Produto com UUID informado não foi encontrado.");
		
		if(dto == null)
			throw new IllegalArgumentException("Produto não foi identificado.");
		
		String oldBarCode = product.getBarCode();
		
		if(dto.barCode() == null || dto.barCode().isBlank())
			throw new IllegalArgumentException("Código de barras inválido.");

		String newBarCode = dto.barCode().trim();
		UUID existingId = barCodeIndex.get(newBarCode);
		if (existingId != null && !existingId.equals(uuid))
			throw new IllegalArgumentException("Já existe outro produto com esse código cadastrado.");

		product.edit(dto.name(), newBarCode, dto.description(), dto.active());
		if (!oldBarCode.equals(newBarCode)) {
			barCodeIndex.remove(oldBarCode);
			barCodeIndex.put(newBarCode, uuid);
		}
		
		return product;
	}
	
	public ProductDTO searchBarCode(String barCode) 
	{
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("O código de barras é obrigatório.");
		
		return Optional.ofNullable(barCodeIndex.get(barCode.trim()))
				.map(products::get)
				.map(product -> new ProductDTO(product.getName(), product.getBarCode(), product.getDescription(), product.isActive()))
				.orElseThrow(() -> new IllegalArgumentException("Esse código de barras não existe."));
	}
	
	public List<ReadOnlyProduct> getProducts(){
		return List.copyOf(products.values());
	}
}
