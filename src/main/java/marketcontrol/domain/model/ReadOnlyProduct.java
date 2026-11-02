package marketcontrol.domain.model;

import java.util.UUID;

public interface ReadOnlyProduct {
	UUID getId();
	String getName();
	String getBarCode();
	String getDescription();
	boolean isActive();
}
