package marketcontrol.http.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import javax.net.ssl.SSLContext;

import marketcontrol.http.server.handlers.AuthHandler;
import marketcontrol.http.server.handlers.AdminUserHandler;
import marketcontrol.http.server.handlers.CategoryHandler;
import marketcontrol.http.server.handlers.CorsHandler;
import marketcontrol.http.server.handlers.ErrorHandlingHandler;
import marketcontrol.http.server.handlers.LotHandler;
import marketcontrol.http.server.handlers.ProductHandler;
import marketcontrol.http.server.handlers.SupplierHandler;
import marketcontrol.service.AuthService;
import marketcontrol.service.CategoryService;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;
import marketcontrol.service.StockMovementService;
import marketcontrol.service.SupplierService;
import marketcontrol.service.UserService;

/** Owns the JDK HTTP server and its virtual-thread executor lifecycle. */
public final class HttpServerApplication implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final boolean https;

    public HttpServerApplication(String host, int port, CategoryService categoryService,
                                 ProductService productService, SupplierService supplierService,
                                 LotService lotService, StockMovementService movementService,
                                 AuthService authService,
                                 UserService userService,
                                 String allowedOrigin, SSLContext sslContext) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(categoryService, "categoryService");
        Objects.requireNonNull(productService, "productService");
        Objects.requireNonNull(supplierService, "supplierService");
        Objects.requireNonNull(lotService, "lotService");
        Objects.requireNonNull(movementService, "movementService");
        Objects.requireNonNull(authService, "authService");
        Objects.requireNonNull(userService, "userService");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Porta HTTP inválida.");

        if (sslContext == null && !InetAddress.getByName(host).isLoopbackAddress())
            throw new IllegalArgumentException("HTTPS é obrigatório para escutar fora do loopback.");

        if (sslContext == null) {
            this.server = HttpServer.create(new InetSocketAddress(host, port), 64);
            this.https = false;
        } else {
            HttpsServer secureServer = HttpsServer.create(new InetSocketAddress(host, port), 64);
            secureServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
            this.server = secureServer;
            this.https = true;
        }
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        ApiRouter router = new ApiRouter(new ProductHandler(productService, lotService),
                new CategoryHandler(categoryService), new SupplierHandler(supplierService),
                new LotHandler(lotService, movementService),
                new AuthHandler(authService), new AdminUserHandler(userService), authService.sessionService());
        server.createContext("/api", new ErrorHandlingHandler(new CorsHandler(router, allowedOrigin)));
    }

    public void start() {
        if (closed.get()) throw new IllegalStateException("O servidor já foi encerrado.");
        if (started.compareAndSet(false, true)) {
            try {
                server.start();
                System.out.printf("MarketControl API disponível em %s://%s:%d/api%n", https ? "https" : "http",
                        server.getAddress().getHostString(), server.getAddress().getPort());
            } catch (RuntimeException ex) {
                started.set(false);
                close();
                throw ex;
            }
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (started.get()) server.stop(1);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
