package marketcontrol.infrastructure.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;

class JdbcTransactionManagerIntegrationTest {
    private final JdbcTransactionManager transactions = new JdbcTransactionManager();
    private final List<UUID> insertedIds = new ArrayList<>();

    @AfterEach void cleanUp() {
        insertedIds.forEach(JdbcTransactionManagerIntegrationTest::deleteCategory);
    }

    @Test void executeCommitsAndRunsCallbackOnce() {
        UUID id = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        transactions.execute(connection -> {
            calls.incrementAndGet();
            insertCategoryAndTrack(connection, id);
        });

        assertEquals(1, calls.get());
        assertEquals(1, categoryCount(id), "execute must commit before returning");
    }

    @Test void executeRejectsNullCallback() {
        assertThrows(IllegalArgumentException.class, () -> transactions.execute(null));
    }

    @Test void executeRollsBackAndPreservesCallbackFailure() {
        UUID id = UUID.randomUUID();
        IllegalStateException expected = new IllegalStateException("callback failure");
        AtomicInteger calls = new AtomicInteger();

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> transactions.execute(connection -> {
            calls.incrementAndGet();
            insertCategoryAndTrack(connection, id);
            throw expected;
        }));

        assertSame(expected, actual);
        assertEquals(1, calls.get());
        assertEquals(0, categoryCount(id), "Callback failure must roll back its insert");
    }

    @Test void executeAndReturnCommitsOnceBeforeReturningCallbackResult() {
        UUID id = UUID.randomUUID();
        Object result = new Object();
        AtomicInteger calls = new AtomicInteger();

        Object returned = transactions.executeAndReturn(connection -> {
            calls.incrementAndGet();
            insertCategoryAndTrack(connection, id);
            return result;
        });

        assertSame(result, returned);
        assertEquals(1, calls.get());
        assertEquals(1, categoryCount(id), "The transaction must be committed when the result is returned");
    }

    @Test void executeAndReturnRejectsNullCallback() {
        assertThrows(IllegalArgumentException.class, () -> transactions.executeAndReturn(null));
    }

    @Test void executeAndReturnRollsBackAndDoesNotReturnWhenCallbackFails() {
        UUID id = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> transactions.executeAndReturn(connection -> {
                    calls.incrementAndGet();
                    insertCategoryAndTrack(connection, id);
                    throw new IllegalArgumentException("callback failure");
                }));

        assertEquals("callback failure", failure.getMessage());
        assertEquals(1, calls.get());
        assertEquals(0, categoryCount(id), "Callback failure must roll back its insert");
    }

    @Test void executeAndReturnWrapsCommitFailureAndKeepsRollbackFailureSuppressed() {
        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> transactions.executeAndReturn(connection -> {
                    closeConnectionToForceCommitFailure(connection);
                    return "must not be returned";
                }));

        assertInstanceOf(SQLException.class, failure.getCause());
        assertTrue(failure.getSuppressed().length > 0,
                "Rollback failure after the connection is closed should be retained as suppressed");
        assertInstanceOf(SQLException.class, failure.getSuppressed()[0]);
    }

    @Test void executePreservesCallbackFailureWhenRollbackAlsoFails() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> transactions.execute(connection -> {
            closeConnectionToForceRollbackFailure(connection);
            throw new IllegalStateException("original callback failure");
        }));

        assertEquals("original callback failure", failure.getMessage());
        assertTrue(failure.getSuppressed().length > 0,
                "Rollback failure should be attached without replacing the callback failure");
        assertInstanceOf(SQLException.class, failure.getSuppressed()[0]);
    }

    private static void closeConnectionToForceCommitFailure(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new AssertionError("Could not close transaction connection in test callback", e);
        }
    }

    private static void closeConnectionToForceRollbackFailure(Connection connection) {
        closeConnectionToForceCommitFailure(connection);
    }

    private static void insertCategory(Connection connection, UUID id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO categories (id, name, description, active) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, id.toString());
            statement.setString(2, "Transaction test " + id);
            statement.setString(3, "Transaction test category");
            statement.setBoolean(4, true);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException e) {
            throw new AssertionError("Could not insert transaction test row", e);
        }
    }

    private void insertCategoryAndTrack(Connection connection, UUID id) {
        insertedIds.add(id);
        insertCategory(connection, id);
    }

    private static int categoryCount(UUID id) {
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM categories WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("Could not inspect transaction test row", e);
        }
    }

    private static void deleteCategory(UUID id) {
        try (Connection connection = DatabaseConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM categories WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new AssertionError("Could not clean transaction test row", e);
        }
    }
}
