package marketcontrol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import marketcontrol.infrastructure.database.DatabaseConnection;

/** Remove only rows created by an integration test, respecting the foreign keys. */
public final class DatabaseTestCleanup {
    private DatabaseTestCleanup() {}

    public static void deleteCategory(UUID categoryId) {
        if (categoryId == null) return;
        try (Connection connection = DatabaseConnection.getConnection()) {
            try (PreparedStatement lots = connection.prepareStatement(
                    "DELETE FROM lots WHERE product_id IN (SELECT id FROM products WHERE category_id = ?)")) {
                lots.setString(1, categoryId.toString());
                lots.executeUpdate();
            }
            try (PreparedStatement products = connection.prepareStatement("DELETE FROM products WHERE category_id = ?")) {
                products.setString(1, categoryId.toString());
                products.executeUpdate();
            }
            try (PreparedStatement categories = connection.prepareStatement("DELETE FROM categories WHERE id = ?")) {
                categories.setString(1, categoryId.toString());
                categories.executeUpdate();
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not clean integration test data", e);
        }
    }
}
