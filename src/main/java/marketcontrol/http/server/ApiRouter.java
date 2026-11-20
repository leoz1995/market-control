package marketcontrol.http.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.http.server.handlers.CategoryHandler;
import marketcontrol.http.server.handlers.HealthHandler;
import marketcontrol.http.server.handlers.LotHandler;
import marketcontrol.http.server.handlers.ProductHandler;
import marketcontrol.http.server.handlers.SupplierHandler;

final class ApiRouter implements HttpHandler {
    private final HealthHandler healthHandler = new HealthHandler();
    private final ProductHandler productHandler;
    private final CategoryHandler categoryHandler;
    private final SupplierHandler supplierHandler;
    private final LotHandler lotHandler;

    ApiRouter(ProductHandler productHandler, CategoryHandler categoryHandler,
              SupplierHandler supplierHandler, LotHandler lotHandler) {
        this.productHandler = productHandler;
        this.categoryHandler = categoryHandler;
        this.supplierHandler = supplierHandler;
        this.lotHandler = lotHandler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 0) {
            throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
        }
        switch (path[0]) {
            case "health" -> {
                if (path.length != 1) throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
                HttpRequests.requireMethod(exchange, "GET");
                healthHandler.handle(exchange);
            }
            case "products" -> productHandler.handle(exchange);
            case "categories" -> categoryHandler.handle(exchange);
            case "suppliers" -> supplierHandler.handle(exchange);
            case "lots" -> lotHandler.handle(exchange);
            default -> throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
        }
    }
}
