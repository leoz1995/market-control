package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.DatabaseTestCleanup;
import marketcontrol.domain.model.StockMovement;
import marketcontrol.domain.model.Supplier;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.infrastructure.database.DatabaseConnection;
import marketcontrol.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcLotRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcStockMovementRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcSupplierRepository;
import marketcontrol.infrastructure.persistence.jdbc.JdbcTransactionManager;

class LotServiceIntegrationTest {
    private final String run = UUID.randomUUID().toString();
    private UUID categoryId;
    private UUID productId;
    private Supplier supplier;
    private final List<UUID> extraSupplierIds = new ArrayList<>();
    private JdbcSupplierRepository supplierRepository;
    private JdbcLotRepository lotRepository;
    private JdbcStockMovementRepository movementRepository;
    private JdbcTransactionManager transactions;
    private LotService lotService;

    @BeforeEach void setUp() {
        try (Connection c = DatabaseConnection.getConnection(); var st = c.createStatement(); var rs = st.executeQuery("SELECT DATABASE()")) {
            assertTrue(rs.next());
            assertEquals("marketcontrol", rs.getString(1), "Integration tests may only use the designated test database");
        } catch (Exception e) { throw new AssertionError("Unable to verify database target", e); }

        var categories = new CategoryService(new JdbcCategoryRepository());
        var category = categories.saveCategory("Lot service " + run, "Lot service fixture", true);
        categoryId = category.getId();
        var products = new ProductService(new JdbcProductRepository(), categories);
        var product = products.saveProduct("Service lot product", "LOT-SVC-" + run, null, categoryId, UnitOfMeasure.UNIT);
        productId = product.getId();
        supplier = Supplier.create("Lot service legal", "Lot service trade", document(run), "lot-" + run + "@example.invalid", "555", "Contact");
        supplierRepository = new JdbcSupplierRepository();
        supplierRepository.save(supplier);

        lotRepository = new JdbcLotRepository(new JdbcProductRepository());
        movementRepository = new JdbcStockMovementRepository();
        transactions = new JdbcTransactionManager();
        SupplierService supplierService = new SupplierService(supplierRepository);
        StockMovementService stockMovements = new StockMovementService(transactions, movementRepository, lotRepository);
        lotService = new LotService(products, lotRepository, supplierService, transactions, stockMovements);
    }

    @AfterEach void cleanUp() {
        DatabaseTestCleanup.deleteCategory(categoryId);
        if (supplier != null) {
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM suppliers WHERE id=?")) {
                p.setString(1, supplier.getId().toString()); p.executeUpdate();
            } catch (Exception e) { throw new AssertionError("Could not clean supplier fixture", e); }
        }
        for (UUID supplierId : extraSupplierIds) {
            try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement("DELETE FROM suppliers WHERE id=?")) {
                p.setString(1, supplierId.toString()); p.executeUpdate();
            } catch (Exception e) { throw new AssertionError("Could not clean extra supplier fixture", e); }
        }
    }

    @Test void initialEntryPersistsLotMovementAndItemAtomically() throws Exception {
        var lot = lotService.save(productId, supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(20), new BigDecimal("8.500"), new BigDecimal("6.40"));
        assertEquals(new BigDecimal("8.500"), lotRepository.findById(lot.getId()).orElseThrow().getAvailableQuantity());
        assertEquals(1, count("SELECT COUNT(*) FROM stock_movements sm JOIN stock_movement_items i ON i.stock_movement_id=sm.id WHERE sm.type=1 AND sm.supplier_id=? AND i.lot_id=?", supplier.getId().toString(), lot.getId().toString()));
        assertEquals(new BigDecimal("6.40"), scalar("SELECT unit_cost FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE i.lot_id=? AND sm.type=1", lot.getId().toString()));
        assertEquals(new BigDecimal("8.500"), scalar("SELECT quantity FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE i.lot_id=? AND sm.type=1", lot.getId().toString()));
    }

    @Test void eachAcquisitionCreatesANewLotAndItsOwnEntryCost() throws Exception {
        var first = lotService.save(productId, supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(20), new BigDecimal("4"), new BigDecimal("2.10"));
        var second = lotService.save(productId, supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("6"), new BigDecimal("2.35"));
        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, lotService.findByProductId(productId).size());
        assertEquals(new BigDecimal("10.000"), lotService.getTotalAvailableQuantity(productId));
        assertEquals(new BigDecimal("2.10"), entryCost(first.getId()));
        assertEquals(new BigDecimal("2.35"), entryCost(second.getId()));
    }

    @Test void rollsBackLotWhenEntryMovementPersistenceFails() throws Exception {
        StockMovementRepository failingMovementRepository = new StockMovementRepository() {
            public void save(Connection c, StockMovement movement) { throw new PersistenceException("controlled failure while saving ENTRY"); }
            public Optional<BigDecimal> findInitialEntryByLotId(Connection c, UUID id) { return Optional.empty(); }
        };
        StockMovementService failingMovements = new StockMovementService(transactions, failingMovementRepository, lotRepository);
        LotService failingService = new LotService(lotServiceProduct(), lotRepository, new SupplierService(supplierRepository), transactions, failingMovements);
        assertThrows(PersistenceException.class, () -> failingService.save(productId, supplier.getId(), LocalDate.now(), LocalDate.now().plusDays(20), BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(0, count("SELECT COUNT(*) FROM lots WHERE product_id=?", productId.toString()));
        assertEquals(0, count("SELECT COUNT(*) FROM stock_movements WHERE supplier_id=? AND description='Entrada inicial do lote.'", supplier.getId().toString()));
    }

    @Test void inactiveSupplierCannotCreateEntryAndLotInsertIsRolledBack() throws Exception {
        UUID inactiveId = UUID.randomUUID();
        Supplier inactive = Supplier.reconstitute(inactiveId, "Inactive legal", "Inactive trade", document(run + "x"), "inactive-" + run + "@example.invalid", "555", "Contact", false, LocalDateTime.now());
        supplierRepository.save(inactive);
        extraSupplierIds.add(inactiveId);
        assertThrows(IllegalArgumentException.class, () -> lotService.save(productId, inactiveId, LocalDate.now(), LocalDate.now().plusDays(20), BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(0, count("SELECT COUNT(*) FROM lots WHERE product_id=?", productId.toString()));
    }

    private ProductService lotServiceProduct() {
        return new ProductService(new JdbcProductRepository(), new CategoryService(new JdbcCategoryRepository()));
    }
    private BigDecimal entryCost(UUID lotId) throws Exception {
        return scalar("SELECT unit_cost FROM stock_movement_items i JOIN stock_movements sm ON sm.id=i.stock_movement_id WHERE i.lot_id=? AND sm.type=1", lotId.toString());
    }
    private static String document(String seed) { return String.format("%014d", Math.abs((long) seed.hashCode()) % 100_000_000_000_000L); }
    private static int count(String sql, String... args) throws Exception {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)p.setString(i+1,args[i]); try(ResultSet rs=p.executeQuery()){rs.next();return rs.getInt(1);} }
    }
    private static BigDecimal scalar(String sql, String... args) throws Exception {
        try (Connection c=DatabaseConnection.getConnection(); PreparedStatement p=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)p.setString(i+1,args[i]); try(ResultSet rs=p.executeQuery()){rs.next();return rs.getBigDecimal(1);} }
    }
}
