package marketcontrol.http.server.handlers;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.domain.model.interfaces.ReadOnlyCategory;
import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.HttpRequests;
import marketcontrol.http.server.responses.ApiModels;
import marketcontrol.http.server.responses.HttpResponseWriter;
import marketcontrol.http.server.status.HttpStatus;
import marketcontrol.service.CategoryService;

public class CategoryHandler implements HttpHandler {
    private final CategoryService categoryService;

    public CategoryHandler(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 1) {
            if ("GET".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "GET");
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                        categoryService.getCategories().stream().map(ApiModels.CategoryResponse::from).toList());
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "POST");
                ApiModels.CategoryRequest request = HttpRequests.readJson(exchange, ApiModels.CategoryRequest.class);
                boolean active = request.active() == null || request.active();
                ReadOnlyCategory category = categoryService.saveCategory(request.name(), request.description(), active);
                exchange.getResponseHeaders().set("Location", "/api/categories/" + category.getId());
                HttpResponseWriter.sendJson(exchange, HttpStatus.CREATED.getCode(), ApiModels.CategoryResponse.from(category));
                return;
            }
            HttpRequests.requireMethod(exchange, "GET", "POST");
        }
        if (path.length == 2) {
            HttpRequests.requireMethod(exchange, "PUT");
            ApiModels.CategoryRequest request = HttpRequests.readJson(exchange, ApiModels.CategoryRequest.class);
            if (request.active() == null) {
                throw new ApiException(400, "MISSING_FIELD", "O campo active é obrigatório para atualizar uma categoria.");
            }
            ReadOnlyCategory category = categoryService.editCategory(HttpRequests.uuid(path[1]), request.name(),
                    request.description(), request.active());
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(), ApiModels.CategoryResponse.from(category));
            return;
        }
        throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
    }
}
