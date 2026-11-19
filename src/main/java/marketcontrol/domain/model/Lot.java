package marketcontrol.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import marketcontrol.domain.model.interfaces.ReadOnlyLot;
import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.exception.data.DataMappingException;

public class Lot implements ReadOnlyLot{

	private final UUID id;
	private final ReadOnlyProduct product;
	private final LocalDate entryDate;
	private final LocalDate expirationDate;
	private BigDecimal availableQuantity;

	private Lot(UUID id, ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal availableQuantity) {
		this.id = id;
		this.product = product;
		this.entryDate = entryDate;
		this.expirationDate = expirationDate;
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
			throw new IllegalArgumentException("O saldo inicial não pode ser zero ou negativo.");

		if(expirationDate.isBefore(entryDate))
			throw new IllegalArgumentException("A data de validade deve ser posterior ou igual a data de entrada.");

		if(entryDate.isAfter(LocalDate.now()))
			throw new IllegalArgumentException("A data de entrada não pode ser no futura.");

		return new Lot(UUID.randomUUID(), product, entryDate, expirationDate, initialQuantity);
	}

	public static Lot reconstitute(UUID id, ReadOnlyProduct product, LocalDate entryDate, LocalDate expirationDate, BigDecimal availableQuantity) {

		if(id == null)
	        throw new DataMappingException("O ID do lote persistido é inválido.");

		if(product == null)
	        throw new DataMappingException("O produto associado ao lote não foi encontrado.");

		if(entryDate == null || expirationDate == null)
	        throw new DataMappingException("As datas do lote persistido são inválidas.");

		if(entryDate.isAfter(LocalDate.now()))
			throw new DataMappingException("A data de entrada não pode ser no futura em lotes persistidos.");

		if(expirationDate.isBefore(entryDate))
			throw new DataMappingException("A validade do lote persistido não pode ser antes da entrada.");

		if(availableQuantity == null)
			throw new DataMappingException("A quantidade disponível do lote persistido não pode ser inválido.");

		if(availableQuantity.compareTo(BigDecimal.ZERO) < 0)
		    throw new DataMappingException("O estoque disponível do lote persistido não pode ser negativo.");

		return new Lot(id, product, entryDate, expirationDate, availableQuantity);
	}

	public BigDecimal reduceQuantity(BigDecimal quantity) {
		if(quantity == null)
			throw new IllegalArgumentException("Quantidade inválida.");

		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A operação não trabalha com dados negativos.");

		if(quantity.compareTo(getAvailableQuantity()) > 0)
			throw new IllegalArgumentException("Estoque atual: "+getAvailableQuantity()+" faltam "+getAvailableQuantity().subtract(quantity)+" para realizar a venda.");

		availableQuantity = availableQuantity.subtract(quantity);

		return availableQuantity;
	}

	public BigDecimal addQuantity(BigDecimal quantity) {
		if(quantity == null)
			throw new IllegalArgumentException("Quantidade inválida.");

		if(quantity.compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("A operação não trabalha com dados negativos.");

		availableQuantity =  availableQuantity.add(quantity);
		return availableQuantity;
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
				+ expirationDate + ", availableQuantity=" + availableQuantity
				+ "]";
	}
}
