package marketcontrol.exception;

public final class TokenExpiredException extends RuntimeException {
    public TokenExpiredException() { super("Sua sessão expirou. Entre novamente."); }
}
