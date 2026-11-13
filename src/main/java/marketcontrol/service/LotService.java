package marketcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.LotRepository;

public class LotService {
	
	private final ProductService productService;
	private final LotRepository lotRepository;
	
	public LotService(ProductService productService, LotRepository lotRepository) {		
		if(productService == null)
			throw new IllegalArgumentException("Serviço de Produto não Instanciado.");
		
		if(lotRepository == null)
			throw new IllegalArgumentException("Repositório de Lot não Instanciado.");
		
		this.productService = productService;
		this.lotRepository = lotRepository;
	}

	public ReadOnlyLot save(UUID productId, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity) {
		if(productId == null)
			throw new IllegalArgumentException("O produto não é válido.");
		
		ReadOnlyProduct product = productService.findById(productId);
		
		if(product == null)
			throw new IllegalArgumentException("ID do produto inválido.");
		
		Lot lot = Lot.create(product, entryDate, expirationDate, initialQuantity);
		lotRepository.save(lot);
		
		return lot;	
	}

	public ReadOnlyLot findById(UUID id){
		Lot lot = lotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("ID inválido."));
		
		if(lot == null)
			throw new IllegalArgumentException("Lote não encontrado.");
		
		return lot;
	}
	
	public List<Lot> findByProductId(UUID productId){
		if(productId == null)
			throw new IllegalArgumentException("Id do produto inválido.");
		
		ReadOnlyProduct p = productService.findById(productId);
		
		if(p == null)
			throw new IllegalArgumentException("ID do produto inválido.");
		
		List<Lot> lots = lotRepository.findByProductId(productId);
		return List.copyOf(lots);
	}
	
	public void seller(UUID lotId, BigDecimal quantity) {
		
		if(lotId == null)
			throw new IllegalArgumentException("Id do lote incorreto.");
		
		if(quantity == null)
			throw new IllegalArgumentException("Quantidade inválida.");
		
		Lot lot = lotRepository.findById(lotId).orElseThrow(() -> new IllegalArgumentException("O lote não está disponível."));
		lot.consume(quantity);
		lotRepository.update(lot);
	}
	
	public BigDecimal getTotalAvailableQuantity(UUID productId) {
		if(productId == null)
			throw new IllegalArgumentException("Insira um ID válido.");
		
		ReadOnlyProduct p = productService.findById(productId);
		
		if(p == null)
			throw new IllegalArgumentException("ID do produto inválido.");
		
		return lotRepository.getTotalAvailableQuantity(productId);
	}
}
