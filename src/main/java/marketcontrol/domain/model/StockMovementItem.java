package marketcontrol.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import marketcontrol.exception.data.DataMappingException;

public class StockMovementItem {

    private final UUID id;
    private final UUID lotId;
    private final BigDecimal quantity;
    private final BigDecimal unitCost;

    private StockMovementItem(UUID id, UUID lotId, BigDecimal quantity, BigDecimal unitCost){
        this.id = id;
        this.lotId = lotId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    public static StockMovementItem create(UUID lotId, BigDecimal quantity, BigDecimal unitCost) {

        if(lotId == null)
            throw new IllegalArgumentException("lotId incorreto.");

        if(quantity == null)
            throw new IllegalArgumentException("A quantidade não pode ser null.");

        if(quantity.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("A quantidade precisa ser maior que zero.");

        if(unitCost == null)
            throw new IllegalArgumentException("O custo unitário não pode ser null.");

        if(unitCost.compareTo(BigDecimal.ZERO) < 1)
            throw new IllegalArgumentException("O preço unitário não pode ser 0 ou null.");

        return new StockMovementItem(UUID.randomUUID(), lotId, quantity, unitCost);
    }


    public static StockMovementItem reconstitute(UUID id, UUID lotId, BigDecimal quantity, BigDecimal unitCost) {

        if(id == null)
            throw new DataMappingException("id da movimentação persistida é obrigarório.");

        if(lotId == null)
            throw new DataMappingException("lotId da movimentação persistida é obrigarório.");

        if(quantity == null)
            throw new DataMappingException("A quantidade da movimentação persistida não pode ser null.");

        if(quantity.compareTo(BigDecimal.ZERO) <= 0)
            throw new DataMappingException("A quantidade da movimentação persistida precisa ser maior que zero.");

        if(unitCost == null || unitCost.compareTo(BigDecimal.ZERO) <= 0)
            throw new DataMappingException("O valor unitário da movimentação persistida precisa ser maior que zero.");

        return new StockMovementItem(id, lotId, quantity, unitCost);
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

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getTotalCost() {
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }
}
