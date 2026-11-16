package marketcontrol.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

import marketcontrol.exception.data.DataMappingException;

public class StockMovementItem {
	
	private final UUID id;
	private final UUID lotId;
	private final BigDecimal quantity;

	private StockMovementItem(UUID id, UUID lotId, BigDecimal quantity){
		this.id = id;
		this.lotId = lotId;
		this.quantity = quantity;
	}
	
	public static StockMovementItem create(UUID lotId, BigDecimal quantity) {		
		
		if(lotId == null)
			throw new IllegalArgumentException("lotId incorreto.");

		if(quantity == null)
			throw new IllegalArgumentException("A quantidade não pode ser null.");
		
		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade precisa ser maior que zero.");
		
		return new StockMovementItem(UUID.randomUUID(), lotId, quantity);
	}

	
	public static StockMovementItem reconstitute(UUID id, UUID lotId, BigDecimal quantity) {		
	
		if(id == null)
			throw new DataMappingException("id da movimentação persistida é obrigarório.");
		
		if(lotId == null)
			throw new DataMappingException("lotId da movimentação persistida é obrigarório.");

		if(quantity == null)
			throw new DataMappingException("A quantidade da movimentação persistida não pode ser null.");
		
		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new DataMappingException("A quantidade da movimentação persistida precisa ser maior que zero.");
		
		return new StockMovementItem(id, lotId, quantity);
	}
	
	public UUID getId() {
		return id;
	}

	public UUID getLotId() {
		return lotId;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}
}
