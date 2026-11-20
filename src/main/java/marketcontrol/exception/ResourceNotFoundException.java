package marketcontrol.exception;

/** Signals that a requested domain resource does not exist. */
public final class ResourceNotFoundException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
