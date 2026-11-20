package marketcontrol.http.server.infrastructure.database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
	
	private static final Properties CONFIG = loadProperties();
	
	private static Properties loadProperties() {
		Properties properties = new Properties();
		
		try(InputStream input = DatabaseConnection.class.getClassLoader().getResourceAsStream("database.properties")){
			if(input == null)
				throw new IllegalArgumentException("Arquivo database.properties não encontrado.");
			
			properties.load(input);
			return properties;
		}catch(IOException e) {
			throw new IllegalArgumentException("Não foi possível carregar as configurações do banco.", e);
		}
	}
	
	public static Connection getConnection() throws SQLException{
		return DriverManager.getConnection(CONFIG.getProperty("db.url"), CONFIG.getProperty("db.user"), CONFIG.getProperty("db.password"));
	}

	/** Administrator connection used only by integration-test fixture cleanup. */
	public static Connection getAdminConnection() throws SQLException {
		String user = System.getProperty("db.admin.user", CONFIG.getProperty("db.admin.user"));
		String password = System.getProperty("db.admin.password", CONFIG.getProperty("db.admin.password"));
		if (user == null || user.isBlank() || password == null)
			throw new SQLException("Database administrator credentials are required for integration-test cleanup.");
		return DriverManager.getConnection(CONFIG.getProperty("db.url"), user, password);
	}
}
