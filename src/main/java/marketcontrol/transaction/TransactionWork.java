package marketcontrol.transaction;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionWork {
	void execute(Connection conn);
}
