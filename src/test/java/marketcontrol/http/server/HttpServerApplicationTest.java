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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.Category;
import marketcontrol.domain.model.Product;
import marketcontrol.domain.model.enums.UnitOfMeasure;
import marketcontrol.domain.repository.CategoryRepository;
import marketcontrol.domain.repository.LotRepository;
import marketcontrol.domain.repository.ProductRepository;
import marketcontrol.domain.repository.StockMovementRepository;
import marketcontrol.domain.repository.SupplierRepository;
import marketcontrol.FakeTransactionManager;
import marketcontrol.http.server.responses.JsonSupport;
import marketcontrol.service.CategoryService;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;
import marketcontrol.service.StockMovementService;
import marketcontrol.service.SupplierService;
import marketcontrol.transaction.TransactionManager;

class HttpServerApplicationTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServerApplication server;

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

        server = new HttpServerApplication("127.0.0.1", 0, categories, products, suppliers, lotService,
                movementService, "http://localhost:4200");
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
    void unknownPathsAndWrongMethodsReturnStructuredErrors() throws Exception {
        HttpResponse<String> oldPath = get("/api/get/products");
        assertEquals(404, oldPath.statusCode());
        assertEquals("NOT_FOUND", JsonSupport.MAPPER.readTree(oldPath.body()).get("error").asText());
        assertTrue(oldPath.headers().firstValue("X-Request-ID").isPresent());

        HttpRequest postHealth = HttpRequest.newBuilder(uri("/api/health"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> method = client.send(postHealth, HttpResponse.BodyHandlers.ofString());
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
        HttpResponse<String> invalid = client.send(invalidJson, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalid.statusCode());
        assertEquals("INVALID_JSON", JsonSupport.MAPPER.readTree(invalid.body()).get("error").asText());

        HttpRequest wrongType = HttpRequest.newBuilder(uri("/api/products"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> unsupported = client.send(wrongType, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, unsupported.statusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", JsonSupport.MAPPER.readTree(unsupported.body()).get("error").asText());

        HttpRequest oversized = HttpRequest.newBuilder(uri("/api/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[JsonSupport.MAX_REQUEST_BYTES + 1]))
                .build();
        HttpResponse<String> tooLarge = client.send(oversized, HttpResponse.BodyHandlers.ofString());
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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElse("").startsWith("/api/products/"));
        assertEquals("Produto HTTP", JsonSupport.MAPPER.readTree(response.body()).get("name").asText());
    }

    @Test
    void corsAllowsConfiguredFrontendAndPreflightsButRejectsOtherOrigins() throws Exception {
        HttpRequest frontendGet = HttpRequest.newBuilder(uri("/api/products"))
                .header("Origin", "http://localhost:4200")
                .GET().build();
        HttpResponse<String> get = client.send(frontendGet, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, get.statusCode());
        assertEquals("http://localhost:4200", get.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(get.headers().firstValue("Access-Control-Expose-Headers").orElse("").contains("Location"));

        HttpRequest preflight = HttpRequest.newBuilder(uri("/api/products"))
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> options = client.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, options.statusCode());
        assertTrue(options.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));

        HttpRequest untrustedOrigin = HttpRequest.newBuilder(uri("/api/products"))
                .header("Origin", "https://untrusted.example")
                .GET().build();
        HttpResponse<String> rejected = client.send(untrustedOrigin, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, rejected.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
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
