package marketcontrol.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.dtos.ProductDTO;

public class ProductService {
	
	private final ProductRepository productRepository;
	private final CategoryService categoryService;
	
	public ProductService(ProductRepository productRepository, CategoryService categoryService) {
		
	    if (productRepository == null)
	        throw new IllegalArgumentException("Repositório de produtos não instanciado.");
	    
		if(categoryService == null)
			throw new IllegalArgumentException("Serviço de categoria não instanciado corretamente.");
		
		this.productRepository = productRepository;
		this.categoryService = categoryService;
	}
	
	public ReadOnlyProduct saveProduct(String name, String barCode, String description, UUID categoryId, UnitOfMeasure unit) {
		ReadOnlyCategory category = categoryService.findById(categoryId);	
		Product product = Product.create(name, barCode, description, category, unit);
		
		String normalizedBarCode = normalizeBarCode(product.getBarCode());
		if(productRepository.existsByBarCode(normalizedBarCode))
			throw new IllegalArgumentException("Já existe um produto com esse código de barras.");
		
		productRepository.save(product);
		
		return product;
	}
	
	public ReadOnlyProduct editProduct(UUID uuid, ProductDTO dto) {
		if(uuid == null)
			throw new IllegalArgumentException("ID inválido.");
		
		if(dto == null)
			throw new IllegalArgumentException("Produto não foi identificado.");

		Product product = productRepository.findById(uuid)
				.orElseThrow(() -> new IllegalArgumentException("Produto com UUID informado não foi encontrado."));
		
		if(dto.unit() != product.getUnit())
		    throw new IllegalArgumentException("A unidade de medida não pode ser alterada.");
		
		String normalizedBarCode = normalizeBarCode(dto.barCode());
		if(productRepository.existsByBarCodeAndIdNot(normalizedBarCode, uuid))
			throw new IllegalArgumentException("Esse código de barra já está em uso por outro produto.");

		ReadOnlyCategory category = categoryService.findById(dto.categoryId());
		
		product.edit(dto.name(), normalizedBarCode, dto.description(), category);
		
		productRepository.update(product);
		
		return product;
	}
	
	public boolean existsBarCode(String barCode) {
		String normalizedBarCode = normalizeBarCode(barCode);	
		return productRepository.existsByBarCode(normalizedBarCode);
	}
	
	public ReadOnlyProduct findById(UUID productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("Produto com UUID informado não foi encontrado."));
	}
	
	public ReadOnlyProduct findByBarCode(String barCode) {
		return productRepository.findByBarCode(barCode).orElseThrow(() -> new IllegalArgumentException("Código de barras não corresponde à um produto cadastrado."));
	}
	
	private String normalizeBarCode(String barCode) {
		if(barCode == null || barCode.isBlank())
			throw new IllegalArgumentException("Código de barras inválido.");
		
		return barCode.trim().toUpperCase(Locale.ROOT);
	}
	
	public List<ReadOnlyProduct> getProducts(){
		return productRepository.findAll().stream().map(prod -> (ReadOnlyProduct) prod).toList();
	}
}
