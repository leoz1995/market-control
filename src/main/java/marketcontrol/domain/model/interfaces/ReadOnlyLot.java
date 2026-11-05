package marketcontrol.domain.model.interfaces;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface ReadOnlyLot {
	UUID getId();
	ReadOnlyProduct getProduct();
	LocalDate getEntryDate();
	LocalDate getExpirationDate();
	BigDecimal getInitialQuantity();
	BigDecimal getAvailableQuantity();
}
