package marketcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.transaction.TransactionManager;

public class LotService{
	
	private final ProductService productService;
	private final LotRepository lotRepository;
	private final TransactionManager transactionManager;
	private final StockMovementService stockMovementService;
	
	public LotService(ProductService productService, LotRepository lotRepository, TransactionManager transactionManager, StockMovementService stockMovementService) {		
		if(productService == null)
			throw new IllegalArgumentException("Serviço de Produto não Instanciado.");
		
		if(lotRepository == null)
			throw new IllegalArgumentException("Repositório de Lot não Instanciado.");
		
		if(stockMovementService == null)
			throw new IllegalArgumentException("Serviço de estoque não disponível no momento.");		
		
		if(transactionManager == null)
			throw new IllegalArgumentException("TransactionManager não iniciado.");
		
		this.productService = productService;
		this.lotRepository = lotRepository;
		this.transactionManager = transactionManager;
		this.stockMovementService = stockMovementService;
	}

	public ReadOnlyLot save(UUID productId, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity) {
		if(productId == null)
			throw new IllegalArgumentException("O produto não é válido.");
		
		ReadOnlyProduct product = productService.findById(productId);
		
		Lot lot = Lot.create(product, entryDate, expirationDate, initialQuantity);

		transactionManager.execute(conn ->{
			lotRepository.save(conn, lot);
			stockMovementService.registerInitialEntry(conn, lot);			
		});
		
		return lot;	
	}

	public ReadOnlyLot findById(UUID id){
		return lotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("ID inválido."));
	}
	
	public List<Lot> findByProductId(UUID productId){
		if(productId == null)
			throw new IllegalArgumentException("Id do produto inválido.");
		
		productService.findById(productId);
		
		List<Lot> lots = lotRepository.findByProductId(productId);
		return List.copyOf(lots);
	}

	public BigDecimal getTotalAvailableQuantity(UUID productId) {
		if(productId == null)
			throw new IllegalArgumentException("Insira um ID válido.");
		
		productService.findById(productId);
		
		return lotRepository.getTotalAvailableQuantity(productId);
	}
}
