package marketcontrol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import marketcontrol.infrastructure.database.DatabaseConnection;

class DataBaseConnectionTest {
    @Test void shouldOpenMariaDbConnection() throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertNotNull(connection);
            assertTrue(connection.isValid(2));
        }
    }
    private static void assertTrue(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }
}
