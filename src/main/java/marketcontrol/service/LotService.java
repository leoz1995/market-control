package marketcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;

public class LotService {
	
	private final ProductService productService;
	private final Map<UUID, ReadOnlyLot> lots = new LinkedHashMap<>();
	
	public LotService(ProductService productService) {		
		this.productService = productService;
	}

	public ReadOnlyLot save(UUID productId, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity) {
		if(productId == null)
			throw new IllegalArgumentException("O produto não é válido.");
		
		Product product = productService.searchById(productId);
		
		if(!product.isActive())
			throw new IllegalArgumentException("O produto está atualmente inativo.");
		
		ReadOnlyLot lot = Lot.create(product, entryDate, expirationDate, initialQuantity);
		lots.put(lot.getId(), lot);
		
		return lot;	
	}

	public Optional<ReadOnlyLot> findById(UUID id){
		if(id == null)
			throw new IllegalArgumentException("Lot ID inexistente.");
		
		ReadOnlyLot search = lots.get(id);
		
		if(search == null)
			return Optional.empty();
				
		return Optional.of(search);
	}
	
	public List<ReadOnlyLot> findByProductId(UUID productId){
		if(productId == null)
			throw new IllegalArgumentException("Id da busca não existe.");

		return lots.values().stream().filter(lot -> lot.getProduct().getId().equals(productId)).toList();
	}
	
	public BigDecimal getTotalAvailableQuantity(UUID productId) {
		if(productId == null)
			throw new IllegalArgumentException("Insira um ID válido.");
		
		BigDecimal total = lots.values().stream()
				.filter(lot -> lot.getProduct().getId().equals(productId))
				.map(ReadOnlyLot::getAvailableQuantity)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return total;
	}
	
	public List<ReadOnlyLot> getLots(){
		return List.copyOf(lots.values());
	}
	
}
