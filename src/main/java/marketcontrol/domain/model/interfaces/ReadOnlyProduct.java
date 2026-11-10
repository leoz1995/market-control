package marketcontrol.domain.model.interfaces;

import java.util.UUID;

public interface ReadOnlyProduct {
	UUID getId();
	String getName();
	String getBarCode();
	String getDescription();
	ReadOnlyCategory getCategory();
}
