package marketcontrol.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;

public class Lot implements ReadOnlyLot{

	private final UUID id;
	private final Product product;
	private final LocalDate entryDate;
	private final LocalDate expirationDate;
	private final BigDecimal initialQuantity;
	private BigDecimal availableQuantity;
	
	private Lot(Product product, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity, BigDecimal availableQuantity) {
		this.id = UUID.randomUUID();
		this.product = product;
		this.entryDate = entryDate;
		this.expirationDate = expirationDate;
		this.initialQuantity = initialQuantity;
		this.availableQuantity = availableQuantity;
	}

	public static Lot create(Product product, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity) {
		
		if(product == null)
			throw new IllegalArgumentException("O produto não existe.");
		
		if(entryDate == null)
			throw new IllegalArgumentException("A data precisa ser válida.");
		
		if(expirationDate == null)
			throw new IllegalArgumentException("A data de expiração precisa ser válida.");

		if(initialQuantity == null)
			throw new IllegalArgumentException("A quantidade recebida precisa ser válida.");
		
		if(initialQuantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("Estoque inicial não aceita valor zero ou negativo.");
			
		if(expirationDate.isBefore(entryDate))
			throw new IllegalArgumentException("A data de validade deve ser posterior ou igual a data de entrada.");
		
		return new Lot(product, entryDate, expirationDate, initialQuantity, initialQuantity);
	}
	
	public void consume(BigDecimal quantity) {
		if(quantity == null)
			throw new IllegalArgumentException("A quantidade consumida é inválida.");
		
		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade solicitada é zero ou negativa.");
		
		if(quantity.compareTo(availableQuantity) > 0)
			throw new IllegalArgumentException("A quantidade em estoque é menor que a venda.");

		if(isExpired())
			throw new IllegalArgumentException("O lote atual é vencido.");
		
		availableQuantity = availableQuantity.subtract(quantity);
	}
	
	public ReadOnlyProduct getProduct() {
		return product;
	}

	public LocalDate getEntryDate() {
		return entryDate;
	}

	public LocalDate getExpirationDate() {
		return expirationDate;
	}

	public BigDecimal getInitialQuantity() {
		return initialQuantity;
	}
	
	public BigDecimal getAvailableQuantity() {
		return availableQuantity;
	}
	
	public boolean isExpired() {
		return expirationDate.isBefore(LocalDate.now());
	}

	public UUID getId() {
		return id;
	}

}
