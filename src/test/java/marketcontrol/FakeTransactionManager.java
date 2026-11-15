package marketcontrol;

import java.sql.Connection;

import marketcontrol.transaction.TransactionAction;
import marketcontrol.transaction.TransactionManager;
import marketcontrol.transaction.TransactionWork;

/** Runs callbacks directly on a supplied connection; it does not model commit or rollback. */
public class FakeTransactionManager implements TransactionManager {
    private final Connection connection;

    public FakeTransactionManager(Connection connection) {
        this.connection = connection;
    }

    @Override public <T> T executeAndReturn(TransactionAction<T> operation) {
        return operation.execute(connection);
    }

    @Override public void execute(TransactionWork operation) {
        operation.execute(connection);
    }
}
