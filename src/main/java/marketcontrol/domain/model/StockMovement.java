package marketcontrol.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import marketcontrol.domain.model.enums.MovementType;

public class StockMovement {

	private final UUID id;
	private final MovementType type;
	private final LocalDateTime dateTime;
	private final String description;
	private final List<StockMovementItem> smi;
	
	private StockMovement(UUID id, MovementType type, LocalDateTime dateTime, String description, List<StockMovementItem> smi){
		this.id = id;
		this.type = type;
		this.dateTime = dateTime;
		this.description = description;
		this.smi = List.copyOf(smi);
	}
	
	public static StockMovement create(MovementType type, LocalDateTime dateTime, String description, List<StockMovementItem> smi)
	{	
		checkMovements(type, dateTime, description, smi);

		return new StockMovement(UUID.randomUUID(), type, dateTime, description, smi);
	}

	public static StockMovement reconstitute(UUID id, MovementType type, LocalDateTime dateTime, String description, List<StockMovementItem> smi) {

		if(id == null)
			throw new IllegalArgumentException("O ID da movimentação é obrigatório.");
		
		checkMovements(type, dateTime, description, smi);

		return new StockMovement(id, type, dateTime, description, smi);
	}
	
	private static void checkMovements(MovementType type, LocalDateTime dateTime, String description, List<StockMovementItem> smi) {
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
	
	public List<StockMovementItem> getItems() {
		return smi;
	}
}
