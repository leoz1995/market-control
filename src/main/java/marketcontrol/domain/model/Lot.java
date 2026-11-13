package marketcontrol.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;

public class Lot implements ReadOnlyLot{

	private final UUID id;
	private final ReadOnlyProduct product;
	private final LocalDate entryDate;
	private final LocalDate expirationDate;
	private final BigDecimal initialQuantity;
	private BigDecimal availableQuantity;
	
	private Lot(UUID id, ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity, BigDecimal availableQuantity) {
		this.id = id;
		this.product = product;
		this.entryDate = entryDate;
		this.expirationDate = expirationDate;
		this.initialQuantity = initialQuantity;
		this.availableQuantity = availableQuantity;
	}

	public static Lot create(ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity) {
		
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
		
		if(entryDate.isAfter(LocalDate.now()))
			throw new IllegalArgumentException("A data de entrada não pode ser no futura.");
			
		return new Lot(UUID.randomUUID(), product, entryDate, expirationDate, initialQuantity, initialQuantity);
	}
	
	public static Lot reconstitute(UUID id, ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal initialQuantity, BigDecimal availableQuantity) {
	
		if(id == null)
			throw new IllegalArgumentException("O ID não pode ser null.");

		if(product == null)
			throw new IllegalArgumentException("O produto não existe.");
		
		if(entryDate == null)
			throw new IllegalArgumentException("A data de entrada não pode ser null.");
		
		if(expirationDate == null)
			throw new IllegalArgumentException("A data de validade não pode ser null.");
		
		if(expirationDate.isBefore(entryDate))
			throw new IllegalArgumentException("A validade não pode ser antes da entrada.");
		
		if(availableQuantity == null)
			throw new IllegalArgumentException("A quantidade disponível não pode ser null.");
		
		if(initialQuantity == null)
			throw new IllegalArgumentException("A quantidade inicial não pode ser null.");
		
		if(availableQuantity.compareTo(BigDecimal.ZERO) < 0)
		    throw new IllegalArgumentException("Estoque disponível não pode ser negativo.");

		if(availableQuantity.compareTo(initialQuantity) > 0)
		    throw new IllegalArgumentException("Estoque disponível não pode superar o estoque inicial.");

		return new Lot(id, product, entryDate, expirationDate, initialQuantity, availableQuantity);
	}
	
	public void consume(BigDecimal quantity) {
		if(quantity == null)
			throw new IllegalArgumentException("A quantidade consumida é inválida.");
		
		if(isExpired())
			throw new IllegalArgumentException("O lote atual é vencido.");

		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade solicitada é zero ou negativa.");
		
		if(quantity.compareTo(availableQuantity) > 0)
			throw new IllegalArgumentException("A quantidade em estoque é menor que a venda.");

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

	@Override
	public String toString() {
		return "Lot [id=" + id + ", product=" + product.getName() + ", entryDate=" + entryDate + ", expirationDate="
				+ expirationDate + ", initialQuantity=" + initialQuantity + ", availableQuantity=" + availableQuantity
				+ "]";
	}
}
