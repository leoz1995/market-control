package marketcontrol.domain.model.interfaces;

import java.util.UUID;

import marketcontrol.domain.model.enums.UnitOfMeasure;

public interface ReadOnlyProduct {
	UUID getId();
	String getName();
	String getBarCode();
	String getDescription();
	ReadOnlyCategory getCategory();
	UnitOfMeasure getUnit();
}
