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
	private final BigDecimal totalReceivedQuantity;
	private final BigDecimal availableQuantity;
	
	private Lot(UUID id, ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal totalReceivedQuantity, BigDecimal availableQuantity) {
		this.id = id;
		this.product = product;
		this.entryDate = entryDate;
		this.expirationDate = expirationDate;
		this.totalReceivedQuantity = totalReceivedQuantity;
		this.availableQuantity = availableQuantity;
	}

	public static Lot create(ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal totalReceivedQuantity) {
		
		if(product == null)
			throw new IllegalArgumentException("O produto não existe.");
		
		if(entryDate == null)
			throw new IllegalArgumentException("A data precisa ser válida.");
		
		if(expirationDate == null)
			throw new IllegalArgumentException("A data de expiração precisa ser válida.");

		if(totalReceivedQuantity == null)
			throw new IllegalArgumentException("A quantidade recebida precisa ser válida.");
		
		if(totalReceivedQuantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("Quantidade total recebida não pode ser zero ou negativa.");
			
		if(expirationDate.isBefore(entryDate))
			throw new IllegalArgumentException("A data de validade deve ser posterior ou igual a data de entrada.");
		
		if(entryDate.isAfter(LocalDate.now()))
			throw new IllegalArgumentException("A data de entrada não pode ser no futura.");
			
		return new Lot(UUID.randomUUID(), product, entryDate, expirationDate, totalReceivedQuantity, totalReceivedQuantity);
	}
	
	public static Lot reconstitute(UUID id, ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal totalReceivedQuantity, BigDecimal availableQuantity) {
	
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
		
		if(totalReceivedQuantity == null)
			throw new IllegalArgumentException("A quantidade total recebida precisa ser válida.");
		
		if(availableQuantity.compareTo(BigDecimal.ZERO) < 0)
		    throw new IllegalArgumentException("Estoque disponível não pode ser negativo.");

		if(availableQuantity.compareTo(totalReceivedQuantity) > 0)
		    throw new IllegalArgumentException("Estoque disponível não pode superar o estoque total.");
		
		if(totalReceivedQuantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A quantidade de estoque total precisa ser maior que zero.");

		return new Lot(id, product, entryDate, expirationDate, totalReceivedQuantity, availableQuantity);
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

	public BigDecimal getTotalReceivedQuantity() {
		return totalReceivedQuantity;
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
				+ expirationDate + ", totalReceivedQuantity=" + totalReceivedQuantity + ", availableQuantity=" + availableQuantity
				+ "]";
	}
}
