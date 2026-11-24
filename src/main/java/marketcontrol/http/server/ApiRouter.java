package marketcontrol.http.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import marketcontrol.domain.model.enums.Role;
import marketcontrol.exception.AuthenticationRequiredException;
import marketcontrol.exception.AuthorizationException;
import marketcontrol.exception.InvalidTokenException;
import marketcontrol.http.server.handlers.AuthHandler;
import marketcontrol.http.server.handlers.AdminUserHandler;
import marketcontrol.http.server.handlers.CategoryHandler;
import marketcontrol.http.server.handlers.HealthHandler;
import marketcontrol.http.server.handlers.LotHandler;
import marketcontrol.http.server.handlers.ProductHandler;
import marketcontrol.http.server.handlers.SupplierHandler;
import marketcontrol.service.SessionService;

final class ApiRouter implements HttpHandler {
    private final HealthHandler healthHandler = new HealthHandler();
    private final ProductHandler productHandler;
    private final CategoryHandler categoryHandler;
    private final SupplierHandler supplierHandler;
    private final LotHandler lotHandler;
    private final AuthHandler authHandler;
    private final AdminUserHandler adminUserHandler;
    private final SessionService sessionService;

    ApiRouter(ProductHandler productHandler, CategoryHandler categoryHandler,
              SupplierHandler supplierHandler, LotHandler lotHandler, AuthHandler authHandler,
              AdminUserHandler adminUserHandler,
              SessionService sessionService) {
        this.productHandler = productHandler;
        this.categoryHandler = categoryHandler;
        this.supplierHandler = supplierHandler;
        this.lotHandler = lotHandler;
        this.authHandler = authHandler;
        this.adminUserHandler = adminUserHandler;
        this.sessionService = sessionService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 0) {
            throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
        }
        if ("health".equals(path[0])) {
            if (path.length != 1) throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
            HttpRequests.requireMethod(exchange, "GET");
            healthHandler.handle(exchange);
            return;
        }
        if ("auth".equals(path[0])) {
            authHandler.handle(exchange);
            return;
        }
        Role role = authenticatedRole(exchange);
        authorize(role, path, exchange.getRequestMethod());
        switch (path[0]) {
            case "products" -> productHandler.handle(exchange);
            case "categories" -> categoryHandler.handle(exchange);
            case "suppliers" -> supplierHandler.handle(exchange);
            case "lots" -> lotHandler.handle(exchange);
            case "users" -> adminUserHandler.handle(exchange);
            default -> throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
        }
    }

    private Role authenticatedRole(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7))
            throw new AuthenticationRequiredException();
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) throw new InvalidTokenException();
        return sessionService.authenticate(token);
    }

    private void authorize(Role role, String[] path, String method) {
        if (role == Role.ADMIN) return;
        boolean allowed = switch (path[0]) {
            case "products" -> "GET".equalsIgnoreCase(method);
            case "categories" -> "GET".equalsIgnoreCase(method);
            case "suppliers" -> "GET".equalsIgnoreCase(method);
            case "lots" -> "GET".equalsIgnoreCase(method)
                    || ("POST".equalsIgnoreCase(method) && (path.length == 1
                        || (path.length == 3 && ("sales".equals(path[2]) || "losses".equals(path[2])))));
            case "users" -> false;
            default -> false;
        };
        if (!allowed) throw new AuthorizationException();
    }
}
