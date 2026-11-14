package marketcontrol.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;
import marketcontrol.transaction.TransactionManager;
import marketcontrol.transaction.TransactionWork;

public class JdbcTransactionManager implements TransactionManager{

	@Override
	public void execute(TransactionWork operation) {
		
		if(operation == null)
			throw new IllegalArgumentException("Operação não informada.");
		
		try(Connection conn = DatabaseConnection.getConnection())
		{	
			conn.setAutoCommit(false);
			
			try {
				operation.execute(conn);
				conn.commit();
			}			
			catch(RuntimeException e) {
				rollback(conn , e);
				throw e;
			}
			catch(SQLException e) {
				PersistenceException exception = new PersistenceException("Não foi possível concluir a operação.", e);
				rollback(conn, exception);
				throw exception;
			}
		}
		catch(SQLException e) {
			throw new PersistenceException("A operação falhou!", e);
		}
	}

	private void rollback(Connection conn, Throwable originalException) {
		try {
			conn.rollback();
		}
		catch(SQLException rollbackException)
		{
			originalException.addSuppressed(rollbackException);
		}
	}
}
