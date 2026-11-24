package marketcontrol.exception;

public final class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() { super("A sessão é inválida. Entre novamente."); }
}
