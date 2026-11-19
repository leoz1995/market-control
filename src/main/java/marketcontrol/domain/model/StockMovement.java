package marketcontrol.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.enums.MovementType;
import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.exception.data.DataMappingException;

public class StockMovement {

	private final UUID id;
	private final MovementType type;
	private final LocalDateTime dateTime;
	private final String description;
	private final ReadOnlySupplier supplier;
	private final List<StockMovementItem> smi;

	private StockMovement(UUID id, MovementType type, LocalDateTime dateTime, String description, ReadOnlySupplier supplier, List<StockMovementItem> smi){
		this.id = id;
		this.type = type;
		this.dateTime = dateTime;
		this.description = description;
		this.supplier = supplier;
		this.smi = List.copyOf(smi);
	}

	public static StockMovement create(MovementType type, LocalDateTime dateTime, String description, ReadOnlySupplier supplier, List<StockMovementItem> smi)
	{
		if(type == null)
			throw new IllegalArgumentException("O tipo da movimentação precisa ser definida.");

		if(dateTime == null)
			throw new IllegalArgumentException("A data da movimentação precisa ser definida.");

		if(description == null || description.isBlank())
			throw new IllegalArgumentException("A descrição da movimentação precisa ser definida.");

		if(smi == null)
			throw new IllegalArgumentException("A lista da movimentação precisa ser inclusa.");

		if(smi.isEmpty())
			throw new IllegalArgumentException("A lista da movimentação não pode ser vazia.");

		if(smi.stream().anyMatch(items -> items == null))
			throw new IllegalArgumentException("A lista não pode ter movimentações null.");

		if(MovementType.ENTRY.equals(type) && (supplier == null || !supplier.isActive()))
			throw new IllegalArgumentException("O fornecedor ativo é obrigatório nas movimentações.");

		if(!MovementType.ENTRY.equals(type) && (supplier != null))
			throw new IllegalArgumentException("O fornecedor só é permitido em movimentações do tipo ENTRY.");

		return new StockMovement(UUID.randomUUID(), type, dateTime, description, supplier, smi);
	}

	public static StockMovement create(MovementType type, LocalDateTime dateTime, String description, List<StockMovementItem> smi){
		return create(type, dateTime, description, null, smi);
	}

	public static StockMovement reconstitute(UUID id, MovementType type, LocalDateTime dateTime, String description, ReadOnlySupplier supplier, List<StockMovementItem> smi) {

		if(id == null)
			throw new DataMappingException("O ID da movimentação persistida é obrigatório.");

		if(type == null)
			throw new DataMappingException("O tipo da movimentação persistida precisa ser definida.");

		if(dateTime == null)
			throw new DataMappingException("A data da movimentação persistida precisa ser definida.");

		if(description == null || description.isBlank())
			throw new DataMappingException("A descrição da movimentação persistida precisa ser definida.");

		if(smi == null)
			throw new DataMappingException("A lista da movimentação persistida precisa ser inclusa.");

		if(smi.isEmpty())
			throw new DataMappingException("A lista da movimentação persistida não pode ser vazia.");

		if(smi.stream().anyMatch(items -> items == null))
			throw new DataMappingException("A lista não pode ter movimentações persistida null.");

		if(MovementType.ENTRY.equals(type) && (supplier == null))
			throw new DataMappingException("O fornecedor é obrigatório nas movimentações persistidas.");

		if(!MovementType.ENTRY.equals(type) && (supplier != null))
			throw new DataMappingException("O fornecedor só é persistido em movimentações do tipo ENTRY.");

		return new StockMovement(id, type, dateTime, description, supplier, smi);
	}

	public UUID getId() {
		return id;
	}

	public MovementType getType() {
		return type;
	}

	public LocalDateTime getDateTime() {
		return dateTime;
	}

	public String getDescription() {
		return description;
	}

	public ReadOnlySupplier getSupplier() {
		return supplier;
	}

	public List<StockMovementItem> getItems() {
		return smi;
	}
}
