package marketcontrol.domain.model.interfaces;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ReadOnlySupplier {

    UUID getId();
    String getLegalName();
    String getTradeName();
    String getDocument();
    String getEmail();
    String getPhone();
    String getContactName();
    boolean isActive();
    LocalDateTime getCreatedAt();
}
