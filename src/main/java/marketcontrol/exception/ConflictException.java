package marketcontrol.exception;

/** Signals a valid request that conflicts with current domain state. */
public final class ConflictException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
