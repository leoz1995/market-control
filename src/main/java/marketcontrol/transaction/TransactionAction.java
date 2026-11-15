package marketcontrol.transaction;

import java.sql.Connection;

public interface TransactionAction<T> {

	T execute(Connection conn);
}
