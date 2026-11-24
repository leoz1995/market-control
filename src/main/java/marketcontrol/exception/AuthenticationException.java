package marketcontrol.exception;

/** Signals invalid login credentials without revealing which value was incorrect. */
public final class AuthenticationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException() {
        super("Credenciais inválidas.");
    }
}
