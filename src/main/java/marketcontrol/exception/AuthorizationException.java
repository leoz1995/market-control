package marketcontrol.exception;

public final class AuthorizationException extends RuntimeException {
    public AuthorizationException() { super("Seu perfil não tem permissão para esta operação."); }
}
