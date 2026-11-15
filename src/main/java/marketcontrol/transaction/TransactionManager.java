package marketcontrol.transaction;

public interface TransactionManager {

	<T> T executeAndReturn(TransactionAction<T> operation);
	void execute(TransactionWork operation);
}
