package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.transaction.TransactionAction;
import marketcontrol.transaction.TransactionManager;
import marketcontrol.transaction.TransactionWork;

class StockMovementServiceTest {
    private final Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a)->null);
    private TransactionManager directTransactions() {
        return new TransactionManager() {
            public void execute(TransactionWork work) { work.execute(connection); }
            public <T> T executeAndReturn(TransactionAction<T> work) { return work.execute(connection); }
        };
    }

    @Test void saleUsesHistoricalEntryCostAndSameConnection() {
        UUID lotId = UUID.randomUUID();
        AtomicReference<Connection> savedConnection = new AtomicReference<>();
        AtomicReference<StockMovement> savedMovement = new AtomicReference<>();
        StockMovementRepository movements = (StockMovementRepository) Proxy.newProxyInstance(StockMovementRepository.class.getClassLoader(), new Class<?>[]{StockMovementRepository.class}, (p,m,a)-> {
            if (m.getName().equals("findInitialEntryByLotId")) { assertSame(connection, a[0]); return Optional.of(new BigDecimal("4.75")); }
            if (m.getName().equals("save")) { savedConnection.set((Connection)a[0]); savedMovement.set((StockMovement)a[1]); return null; }
            return null;
        });
        LotRepository lots = (LotRepository) Proxy.newProxyInstance(LotRepository.class.getClassLoader(), new Class<?>[]{LotRepository.class}, (p,m,a)-> {
            if (m.getName().equals("decreaseAvailableQuantityForSale")) { savedConnection.set((Connection)a[0]); return 1; }
            return defaultValue(m.getReturnType());
        });
        new StockMovementService(directTransactions(), movements, lots).registerSale(lotId, new BigDecimal("2"));
        assertSame(connection, savedConnection.get());
        assertEquals(new BigDecimal("4.75"), savedMovement.get().getItems().getFirst().getUnitCost());
    }

    @Test void rejectsSaleWithoutInitialEntryBeforeChangingLot() {
        AtomicReference<Boolean> updateCalled = new AtomicReference<>(false);
        StockMovementRepository movements = (StockMovementRepository) Proxy.newProxyInstance(StockMovementRepository.class.getClassLoader(), new Class<?>[]{StockMovementRepository.class}, (p,m,a)-> m.getName().equals("findInitialEntryByLotId") ? Optional.empty() : null);
        LotRepository lots = (LotRepository) Proxy.newProxyInstance(LotRepository.class.getClassLoader(), new Class<?>[]{LotRepository.class}, (p,m,a)-> { if (m.getName().equals("decreaseAvailableQuantityForSale")) updateCalled.set(true); return defaultValue(m.getReturnType()); });
        StockMovementService service = new StockMovementService(directTransactions(), movements, lots);
        assertThrows(IllegalStateException.class, () -> service.registerSale(UUID.randomUUID(), BigDecimal.ONE));
        assertFalse(updateCalled.get());
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
