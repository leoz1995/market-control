package marketcontrol.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.Lot;
import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.StockMovementItem;
import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.transaction.TransactionManager;

public class StockMovementService {

	private final TransactionManager transactionManager;
	private final StockMovementRepository stockMovementRepository;
	private final LotRepository lotRepository;

	public StockMovementService(TransactionManager transactionManager, StockMovementRepository stockMovementRepository, LotRepository lotRepository){

		if(transactionManager == null)
			throw new IllegalArgumentException("TransactionManager não instanciado.");

		if(stockMovementRepository == null)
			throw new IllegalArgumentException("StockMovementRepository null.");

		if(lotRepository == null)
			throw new IllegalArgumentException("LotRepository null.");

		this.transactionManager = transactionManager;
		this.stockMovementRepository = stockMovementRepository;
		this.lotRepository = lotRepository;
	}

	public void registerInitialEntry(Connection conn, ReadOnlySupplier supplier,  Lot lot, BigDecimal unitValue) {
	    if (conn == null)
	        throw new IllegalArgumentException("Conexão inválida.");

	    if (lot == null)
	        throw new IllegalArgumentException("O lote não pode ser nulo.");

	    if (supplier == null)
	        throw new IllegalArgumentException("O fornecedor é obrigatório.");

		StockMovement stockMovement = StockMovement.create(MovementType.ENTRY, LocalDateTime.now(),
				"Entrada inicial do lote.", supplier, List.of(StockMovementItem.create(lot.getId(), lot.getAvailableQuantity(), unitValue)));

		stockMovementRepository.saveInitialEntry(conn, stockMovement);
	}

	public void registerSale(UUID lotId, BigDecimal quantity) {
		check(lotId, quantity);

		transactionManager.execute(conn -> {
			StockMovement stockMovement = createMovement(conn, lotId, quantity, MovementType.EXIT, "Produto vendido.");

			int affectedRows = lotRepository.decreaseAvailableQuantityForSale(conn, lotId, quantity);

			if(affectedRows == 1)
				stockMovementRepository.save(conn, stockMovement);
			else if(affectedRows == 0)
				throw new IllegalArgumentException("Lote inexistente, validade ultrapassada ou quantidade insuficiente.");
			else
				throw new IllegalStateException("Situação inesperada na operação de venda.");
		});
	}

	public void registerLoss(UUID lotId, BigDecimal quantity, String lossCause) {

		if (lossCause == null || lossCause.isBlank())
		    throw new IllegalArgumentException(
		        "A causa da perda precisa ser informada."
		    );

		check(lotId, quantity);

		transactionManager.execute(conn -> {
			StockMovement stockMovement = createMovement(conn, lotId, quantity,MovementType.LOSS, lossCause);
			int affectedRows = lotRepository.decreaseAvailableQuantityForLoss(conn, lotId, quantity);

			if(affectedRows == 1)
				stockMovementRepository.save(conn, stockMovement);
			else if(affectedRows == 0)
				throw new IllegalArgumentException("Lote inexistente, quantidade insuficiente ou produto inexistente.");
			else
				throw new IllegalStateException("Situação inesperada na operação de perda.");
		});
	}
	private StockMovement createMovement(Connection conn, UUID lotId, BigDecimal quantity, MovementType type, String cause) {
		BigDecimal unitCost = stockMovementRepository.findInitialEntryByLotId(conn, lotId)
			    .orElseThrow(() -> new IllegalStateException("Custo de aquisição não encontrado para o lote."));

		return StockMovement.create(type, LocalDateTime.now(), cause,
				List.of(StockMovementItem.create(lotId, quantity, unitCost)));
	}
	private void check(UUID lotId, BigDecimal quantity) {

		if(lotId == null)
			throw new IllegalArgumentException("O lote precisa ser válido.");

		if(quantity == null)
			throw new IllegalArgumentException("A quantidade precisa ser definida.");

		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
	}
}
