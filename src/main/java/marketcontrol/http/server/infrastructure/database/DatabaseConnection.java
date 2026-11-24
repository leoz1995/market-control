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
				return properties;
			
			properties.load(input);
			return properties;
		}catch(IOException e) {
			throw new IllegalArgumentException("Não foi possível carregar as configurações do banco.", e);
		}
	}
	
	public static Connection getConnection() throws SQLException{
		String url = setting("MARKETCONTROL_DB_URL", "db.url");
		String user = setting("MARKETCONTROL_DB_USER", "db.user");
		String password = setting("MARKETCONTROL_DB_PASSWORD", "db.password");
		if (url == null || user == null || password == null)
			throw new SQLException("Configure MARKETCONTROL_DB_URL, MARKETCONTROL_DB_USER e MARKETCONTROL_DB_PASSWORD ou database.properties.");
		return DriverManager.getConnection(url, user, password);
	}

	private static String setting(String envName, String propertyName) {
		String value = System.getenv(envName);
		return value == null || value.isBlank() ? CONFIG.getProperty(propertyName) : value;
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
