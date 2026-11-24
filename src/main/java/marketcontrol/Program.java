package marketcontrol;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import marketcontrol.domain.repository.CategoryRepository;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.domain.repository.UserRepository;
import marketcontrol.http.server.HttpServerApplication;
import marketcontrol.http.server.infrastructure.TlsConfiguration;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcCategoryRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcLotRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcProductRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcStockMovementRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcSupplierRepository;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcTransactionManager;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcUserRepository;
import marketcontrol.security.SecurityPassword;
import marketcontrol.service.AuthService;
import marketcontrol.service.CategoryService;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;
import marketcontrol.service.SessionService;
import marketcontrol.service.StockMovementService;
import marketcontrol.service.SupplierService;
import marketcontrol.service.UserService;
import marketcontrol.transaction.TransactionManager;

/** Application entry point. MarketControl runs as an HTTP API; it has no console menu. */
public final class Program {

    private Program() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        ProductRepository productRepository = new JdbcProductRepository();
        CategoryRepository categoryRepository = new JdbcCategoryRepository();
        SupplierRepository supplierRepository = new JdbcSupplierRepository();
        LotRepository lotRepository = new JdbcLotRepository(productRepository);
        StockMovementRepository movementRepository = new JdbcStockMovementRepository();
        TransactionManager transactionManager = new JdbcTransactionManager();
        UserRepository userService = new JdbcUserRepository();

        CategoryService categoryService = new CategoryService(categoryRepository);
        ProductService productService = new ProductService(productRepository, categoryService);
        SupplierService supplierService = new SupplierService(supplierRepository);
        LotService lotService = new LotService(productService, lotRepository, supplierService, transactionManager);
        StockMovementService movementService = new StockMovementService(transactionManager, movementRepository, lotRepository);
        SecurityPassword sessionSecurity = new SecurityPassword();
        SessionService sessionService = new SessionService();

        AuthService authService = new AuthService(userService, sessionSecurity, sessionService);
        UserService accountService = new UserService(sessionSecurity, userService);

        int port = Integer.getInteger("marketcontrol.http.port", 8080);
        String host = System.getProperty("marketcontrol.http.host", "127.0.0.1");
        String allowedOrigin = System.getProperty("marketcontrol.http.cors.allowed-origin", "http://localhost:4200");
        HttpServerApplication server = new HttpServerApplication(host, port, categoryService, productService,
                supplierService, lotService, movementService, authService, accountService, allowedOrigin,
                TlsConfiguration.load());
        server.start();

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            stopped.countDown();
        }, "marketcontrol-shutdown"));

        try {
            stopped.await();
        } catch (InterruptedException ex) {
            server.close();
            Thread.currentThread().interrupt();
            throw ex;
        }
    }
}
