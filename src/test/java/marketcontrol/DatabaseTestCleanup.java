package marketcontrol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import marketcontrol.infrastructure.database.DatabaseConnection;

/** Remove only rows created by an integration test, respecting the foreign keys. */
public final class DatabaseTestCleanup {
    private DatabaseTestCleanup() {}

    public static void deleteCategory(UUID categoryId) {
        if (categoryId == null) return;
        try (Connection connection = DatabaseConnection.getAdminConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute("SET @marketcontrol_allow_origin_delete = 1");
            }
            List<String> movementIds = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT DISTINCT smi.stock_movement_id FROM stock_movement_items smi JOIN lots l ON l.id = smi.lot_id JOIN products p ON p.id = l.product_id WHERE p.category_id = ?")) {
                query.setString(1, categoryId.toString());
                try (ResultSet results = query.executeQuery()) {
                    while (results.next()) movementIds.add(results.getString(1));
                }
            }
            try (PreparedStatement items = connection.prepareStatement(
                    "DELETE FROM stock_movement_items WHERE lot_id IN (SELECT l.id FROM lots l JOIN products p ON p.id = l.product_id WHERE p.category_id = ?)")) {
                items.setString(1, categoryId.toString());
                items.executeUpdate();
            }
            try (PreparedStatement movements = connection.prepareStatement("DELETE FROM stock_movements WHERE id = ?")) {
                for (String movementId : movementIds) {
                    movements.setString(1, movementId);
                    movements.addBatch();
                }
                movements.executeBatch();
            }
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
