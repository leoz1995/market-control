package marketcontrol.http.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.dtos.auth.CreateUserRequest;
import marketcontrol.dtos.auth.RoleRequest;
import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.HttpRequests;
import marketcontrol.http.server.responses.HttpResponseWriter;
import marketcontrol.http.server.status.HttpStatus;
import marketcontrol.service.UserService;

public final class AdminUserHandler implements HttpHandler {
    private final UserService users;

    public AdminUserHandler(UserService users) { this.users = users; }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 1) {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "GET");
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(), users.listUsers());
                return;
            }
            HttpRequests.requireMethod(exchange, "POST");
            CreateUserRequest request = HttpRequests.readJson(exchange, CreateUserRequest.class);
            Role role = request.role() == null ? Role.INACTIVE : request.role();
            var user = users.register(request.name(), request.email(), request.password(), role);
            exchange.getResponseHeaders().set("Location", "/api/users/" + user.id());
            HttpResponseWriter.sendJson(exchange, HttpStatus.CREATED.getCode(), user);
            return;
        }
        if (path.length == 3 && "role".equals(path[2])) {
            HttpRequests.requireMethod(exchange, "PUT");
            RoleRequest request = HttpRequests.readJson(exchange, RoleRequest.class);
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    users.changeRole(HttpRequests.uuid(path[1]), request.role()));
            return;
        }
        if (path.length == 3 && "activation".equals(path[2])) {
            HttpRequests.requireMethod(exchange, "POST");
            RoleRequest request = HttpRequests.readJson(exchange, RoleRequest.class);
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    users.activate(HttpRequests.uuid(path[1]), request.role()));
            return;
        }
        if (path.length == 3 && "deactivation".equals(path[2])) {
            HttpRequests.requireMethod(exchange, "POST");
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    users.changeRole(HttpRequests.uuid(path[1]), Role.INACTIVE));
            return;
        }
        throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
    }
}
