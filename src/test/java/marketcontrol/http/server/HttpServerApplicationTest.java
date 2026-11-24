package marketcontrol.http.server;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.CategoryRepository;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.domain.repository.SessionRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.domain.repository.UserRepository;
import marketcontrol.FakeTransactionManager;
import marketcontrol.http.server.responses.JsonSupport;
import marketcontrol.service.CategoryService;
import marketcontrol.service.AuthService;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;
import marketcontrol.service.SessionService;
import marketcontrol.service.StockMovementService;
import marketcontrol.service.SupplierService;
import marketcontrol.service.UserService;
import marketcontrol.security.SecurityPassword;
import marketcontrol.transaction.TransactionManager;

class HttpServerApplicationTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServerApplication server;
    private String accessToken;
    private SessionService sessions;
    private Map<UUID, Role> sessionRoles;

    @BeforeEach
    void startServer() throws Exception {
        Category category = Category.reconstitute(UUID.randomUUID(), "Café Básico", "Categoria de teste", true);
        Product product = Product.reconstitute(UUID.randomUUID(), "Café especial", "CAFE-TESTE", "Descrição", category,
                UnitOfMeasure.KILOGRAM);
        CategoryService categories = new CategoryService(categoryRepository(category));
        ProductService products = new ProductService(productRepository(product), categories);
        SupplierService suppliers = new SupplierService(supplierRepository());
        LotRepository lots = lotRepository();
        StockMovementRepository movements = movementRepository();
        TransactionManager transactions = new FakeTransactionManager(null);
        LotService lotService = new LotService(products, lots, suppliers, transactions);
        StockMovementService movementService = new StockMovementService(transactions, movements, lots);

        Map<UUID, SessionRepository.ActiveSession> activeSessions = new ConcurrentHashMap<>();
        sessionRoles = new ConcurrentHashMap<>();
        SessionRepository sessionRepository = new SessionRepository() {
            @Override public void create(UUID id, UUID userId, UUID tokenId, String hash, LocalDateTime expiresAt) {
                activeSessions.put(id, new ActiveSession(id, userId, tokenId, sessionRoles.get(userId)));
            }
            @Override public void pruneExpired() {}
            @Override public Optional<ActiveSession> findActive(UUID id, UUID tokenId) {
                return Optional.ofNullable(activeSessions.get(id))
                        .filter(session -> session.accessTokenId().equals(tokenId));
            }
            @Override public Optional<ActiveSession> findByRefreshTokenHash(String hash) { return Optional.empty(); }
            @Override public boolean rotate(UUID id, String oldHash, UUID tokenId, String nextHash) { return false; }
            @Override public void revokeByRefreshTokenHash(String hash) {}
        };
        sessions = new SessionService("test-secret-value-at-least-32-bytes-long", sessionRepository);
        accessToken = issueToken(Role.ADMIN);
        UserRepository users = (UserRepository) java.lang.reflect.Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of();
                    case "findById", "authUser" -> Optional.empty();
                    case "userEmailExists" -> false;
                    case "save", "updateRole" -> null;
                    case "toString" -> "UserRepositoryStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        SecurityPassword passwordSecurity = new SecurityPassword();
        AuthService authService = new AuthService(users, passwordSecurity, sessions);
        UserService userService = new UserService(passwordSecurity, users);

        server = new HttpServerApplication("127.0.0.1", 0, categories, products, suppliers, lotService,
                movementService, authService, userService, "http://localhost:4200", null);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.close();
    }

    @Test
    void healthAndProductsReturnJsonAndProductResponseUsesExplicitFields() throws Exception {
        HttpResponse<String> health = get("/api/health");
        assertEquals(200, health.statusCode());
        assertTrue(health.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertEquals("UP", JsonSupport.MAPPER.readTree(health.body()).get("status").asText());

        HttpResponse<String> response = get("/api/products");
        assertEquals(200, response.statusCode());
        var product = JsonSupport.MAPPER.readTree(response.body()).get(0);
        assertEquals("Café especial", product.get("name").asText());
        assertEquals("KILOGRAM", product.get("unit").asText());
        assertEquals("Café Básico", product.get("category").get("name").asText());
        assertTrue(product.get("category").get("active").asBoolean());
    }

    @Test
    void protectedRoutesRequireAuthenticationAndRestrictAdminOperations() throws Exception {
        HttpRequest anonymousRequest = HttpRequest.newBuilder(uri("/api/products")).GET().build();
        HttpResponse<String> anonymous = client.send(anonymousRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());
        assertEquals("AUTHENTICATION_REQUIRED", JsonSupport.MAPPER.readTree(anonymous.body()).get("error").asText());

        String operatorToken = issueToken(Role.STOCK_OPERATOR);
        HttpRequest adminRequest = HttpRequest.newBuilder(uri("/api/users")).GET().build();
        HttpResponse<String> forbidden = send(adminRequest, operatorToken);
        assertEquals(403, forbidden.statusCode());
        assertEquals("FORBIDDEN", JsonSupport.MAPPER.readTree(forbidden.body()).get("error").asText());
    }

    @Test
    void unknownPathsAndWrongMethodsReturnStructuredErrors() throws Exception {
        HttpResponse<String> oldPath = get("/api/get/products");
        assertEquals(404, oldPath.statusCode());
        assertEquals("NOT_FOUND", JsonSupport.MAPPER.readTree(oldPath.body()).get("error").asText());
        assertTrue(oldPath.headers().firstValue("X-Request-ID").isPresent());

        HttpRequest postHealth = HttpRequest.newBuilder(uri("/api/health"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> method = send(postHealth);
        assertEquals(405, method.statusCode());
        assertEquals("GET", method.headers().firstValue("Allow").orElseThrow());
        assertEquals("METHOD_NOT_ALLOWED", JsonSupport.MAPPER.readTree(method.body()).get("error").asText());
    }

    @Test
    void requestBodyValidationReturnsBadRequestAndUnsupportedMediaType() throws Exception {
        HttpRequest invalidJson = HttpRequest.newBuilder(uri("/api/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{"))
                .build();
        HttpResponse<String> invalid = send(invalidJson);
        assertEquals(400, invalid.statusCode());
        assertEquals("INVALID_JSON", JsonSupport.MAPPER.readTree(invalid.body()).get("error").asText());

        HttpRequest wrongType = HttpRequest.newBuilder(uri("/api/products"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> unsupported = send(wrongType);
        assertEquals(415, unsupported.statusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", JsonSupport.MAPPER.readTree(unsupported.body()).get("error").asText());

        HttpRequest oversized = HttpRequest.newBuilder(uri("/api/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[JsonSupport.MAX_REQUEST_BYTES + 1]))
                .build();
        HttpResponse<String> tooLarge = send(oversized);
        assertEquals(413, tooLarge.statusCode());
        assertEquals("PAYLOAD_TOO_LARGE", JsonSupport.MAPPER.readTree(tooLarge.body()).get("error").asText());
    }

    @Test
    void productCreationReturnsJsonAndLocation() throws Exception {
        String categoryId = JsonSupport.MAPPER.readTree(get("/api/products").body())
                .get(0).get("category").get("id").asText();
        String json = "{\"name\":\"Produto HTTP\",\"barCode\":\"HTTP-NEW\","
                + "\"description\":null,\"categoryId\":\"" + categoryId + "\",\"unit\":\"UNIT\"}";
        HttpRequest request = HttpRequest.newBuilder(uri("/api/products"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElse("").startsWith("/api/products/"));
        assertEquals("Produto HTTP", JsonSupport.MAPPER.readTree(response.body()).get("name").asText());
    }

    @Test
    void corsAllowsConfiguredFrontendAndPreflightsButRejectsOtherOrigins() throws Exception {
        HttpRequest frontendGet = HttpRequest.newBuilder(uri("/api/products"))
                .header("Origin", "http://localhost:4200")
                .GET().build();
        HttpResponse<String> get = send(frontendGet);
        assertEquals(200, get.statusCode());
        assertEquals("http://localhost:4200", get.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(get.headers().firstValue("Access-Control-Expose-Headers").orElse("").contains("Location"));

        HttpRequest preflight = HttpRequest.newBuilder(uri("/api/products"))
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> options = send(preflight);
        assertEquals(204, options.statusCode());
        assertTrue(options.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));

        HttpRequest untrustedOrigin = HttpRequest.newBuilder(uri("/api/products"))
                .header("Origin", "https://untrusted.example")
                .GET().build();
        HttpResponse<String> rejected = send(untrustedOrigin);
        assertEquals(403, rejected.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    private HttpResponse<String> send(HttpRequest original) throws Exception {
        return send(original, accessToken);
    }

    private HttpResponse<String> send(HttpRequest original, String token) throws Exception {
        HttpRequest.Builder authorized = HttpRequest.newBuilder(original.uri());
        original.headers().map().forEach((name, values) -> values.forEach(value -> authorized.header(name, value)));
        authorized.header("Authorization", "Bearer " + token);
        authorized.method(original.method(), original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        return client.send(authorized.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String issueToken(Role role) {
        UUID userId = UUID.randomUUID();
        sessionRoles.put(userId, role);
        return sessions.create(userId, role).accessToken();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getPort() + path);
    }

    private static CategoryRepository categoryRepository(Category category) {
        return (CategoryRepository) java.lang.reflect.Proxy.newProxyInstance(CategoryRepository.class.getClassLoader(),
                new Class<?>[]{CategoryRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(category);
                    case "findById" -> Optional.of(category).filter(value -> value.getId().equals(args[0]));
                    case "existsById", "existsByName", "existsByNameAndIdNot" -> false;
                    case "save", "update" -> null;
                    case "toString" -> "CategoryRepositoryStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ProductRepository productRepository(Product product) {
        return (ProductRepository) java.lang.reflect.Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),
                new Class<?>[]{ProductRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> List.of(product);
                    case "findProductById", "findById" -> Optional.of(product).filter(value -> value.getId().equals(args[args.length - 1]));
                    case "findByBarCode" -> Optional.of(product).filter(value -> value.getBarCode().equals(args[0]));
                    case "existsByBarCode", "existsByBarCodeAndIdNot" -> false;
                    case "save", "update" -> null;
                    case "toString" -> "ProductRepositoryStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static SupplierRepository supplierRepository() {
        return (SupplierRepository) java.lang.reflect.Proxy.newProxyInstance(SupplierRepository.class.getClassLoader(),
                new Class<?>[]{SupplierRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.empty();
                    case "existsByDocument" -> false;
                    case "save", "update" -> null;
                    case "toString" -> "SupplierRepositoryStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LotRepository lotRepository() {
        return (LotRepository) java.lang.reflect.Proxy.newProxyInstance(LotRepository.class.getClassLoader(),
                new Class<?>[]{LotRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.empty();
                    case "findByProductId" -> List.of();
                    case "getTotalAvailableQuantity" -> BigDecimal.ZERO;
                    case "decreaseAvailableQuantityForSale", "decreaseAvailableQuantityForLoss" -> 0;
                    case "saveWithInitialEntry" -> null;
                    case "toString" -> "LotRepositoryStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static StockMovementRepository movementRepository() {
        return (StockMovementRepository) java.lang.reflect.Proxy.newProxyInstance(StockMovementRepository.class.getClassLoader(),
                new Class<?>[]{StockMovementRepository.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "findInitialEntryByLotId" -> Optional.empty();
                    case "save", "saveInitialEntry" -> null;
                    case "toString" -> "MovementRepositoryStub";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
