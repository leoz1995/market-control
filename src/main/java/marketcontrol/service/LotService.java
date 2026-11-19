package marketcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.transaction.TransactionManager;

public class LotService{

	private final ProductService productService;
	private final LotRepository lotRepository;
	private final SupplierService supplierService;
	private final TransactionManager transactionManager;

	public LotService(ProductService productService, LotRepository lotRepository, SupplierService supplierService, TransactionManager transactionManager) {
		if(productService == null)
			throw new IllegalArgumentException("Serviço de Produto não Instanciado.");

		if(lotRepository == null)
			throw new IllegalArgumentException("Repositório de Lot não Instanciado.");

		if(supplierService == null)
			throw new IllegalArgumentException("Serviço Supplier não disponível no momento.");

		if(transactionManager == null)
			throw new IllegalArgumentException("TransactionManager não iniciado.");

		this.productService = productService;
		this.lotRepository = lotRepository;
		this.supplierService = supplierService;
		this.transactionManager = transactionManager;
	}

	public ReadOnlyLot save(UUID productId, UUID supplierId, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity, BigDecimal unitCost) {
		if(productId == null)
			throw new IllegalArgumentException("O produto não é válido.");

		if(supplierId == null)
			throw new IllegalArgumentException("O Supplier não é válido.");

		ReadOnlyProduct product = productService.findById(productId);
		ReadOnlySupplier supplier = supplierService.findById(supplierId);
		if (!supplier.isActive())
			throw new IllegalArgumentException("Um fornecedor inativo não pode registrar novas entradas.");

		Lot lot = Lot.create(product, entryDate, expirationDate, initialQuantity);

		transactionManager.execute(conn ->{
			lotRepository.saveWithInitialEntry(conn, lot, supplier, unitCost);
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
