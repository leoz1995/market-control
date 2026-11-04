package marketcontrol.domain.model.interfaces;

import java.util.UUID;

public interface ReadOnlyCategory {
	UUID getId();
	String getName();
	String getDescription();
	boolean isActive();
}
