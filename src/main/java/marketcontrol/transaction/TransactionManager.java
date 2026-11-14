package marketcontrol.transaction;

public interface TransactionManager {

	void execute(TransactionWork operation);
}
