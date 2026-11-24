package marketcontrol.exception;

public final class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() { super("Autenticação obrigatória para acessar este recurso."); }
}
