package marketcontrol.exception;

/** Signals that valid credentials belong to an account that is not active. */
public final class InactiveAccountException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InactiveAccountException() {
        super("Usuário sem atividade. Entre em contato com o administrador.");
    }
}
