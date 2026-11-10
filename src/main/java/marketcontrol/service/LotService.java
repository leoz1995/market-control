package marketcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;

public class LotService {
	
	private final ProductService productService;
	private final Map<UUID, Lot> lots = new LinkedHashMap<>();
	
	public LotService(ProductService productService) {		
		if(productService == null)
			throw new IllegalArgumentException("Serviço de Produto não Instanciado.");
		
		this.productService = productService;
	}

	public ReadOnlyLot save(UUID productId, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity) {
		if(productId == null)
			throw new IllegalArgumentException("O produto não é válido.");
		
		Product product = productService.searchById(productId);
		
		if(product == null)
			throw new IllegalArgumentException("ID do produto inválido.");
		
		Lot lot = Lot.create(product, entryDate, expirationDate, initialQuantity);
		lots.put(lot.getId(), lot);
		
		return lot;	
	}

	public ReadOnlyLot findById(UUID id){
		if(id == null)
			throw new IllegalArgumentException("Lot ID inexistente.");
		
		Lot lot = lots.get(id);
		if(lot == null)
			throw new IllegalArgumentException("Lote não encontrado.");
		
		return lot;
	}
	
	public List<ReadOnlyLot> findByProductId(UUID productId){
		if(productId == null)
			throw new IllegalArgumentException("Id do produto inválido.");
		
		ReadOnlyProduct p = productService.searchById(productId);
		if(p == null)
			throw new IllegalArgumentException("ID do produto inválido.");
		
		return lots.values().stream().filter(lot -> lot.getProduct().getId().equals(productId)).map(lot -> (ReadOnlyLot) lot).toList();
	}
	
	public BigDecimal getTotalAvailableQuantity(UUID productId) {
		if(productId == null)
			throw new IllegalArgumentException("Insira um ID válido.");
		
		ReadOnlyProduct p = productService.searchById(productId);
		
		if(p == null)
			throw new IllegalArgumentException("ID do produto inválido.");
		
		return lots.values().stream()
				.filter(lot -> lot.getProduct().getId().equals(productId) && !lot.isExpired())
				.map(ReadOnlyLot::getAvailableQuantity)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}
