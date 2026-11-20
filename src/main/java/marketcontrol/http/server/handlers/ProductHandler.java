package marketcontrol.http.server.handlers;

import java.io.IOException;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.domain.model.interfaces.ReadOnlyProduct;
import marketcontrol.dtos.ProductDTO;
import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.HttpRequests;
import marketcontrol.http.server.responses.ApiModels;
import marketcontrol.http.server.responses.HttpResponseWriter;
import marketcontrol.http.server.status.HttpStatus;
import marketcontrol.service.LotService;
import marketcontrol.service.ProductService;

public class ProductHandler implements HttpHandler {
    private final ProductService productService;
    private final LotService lotService;

    public ProductHandler(ProductService productService, LotService lotService) {
        this.productService = productService;
        this.lotService = lotService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 1) {
            if ("GET".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "GET");
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                        productService.getProducts().stream().map(ApiModels.ProductResponse::from).toList());
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "POST");
                ApiModels.ProductRequest request = HttpRequests.readJson(exchange, ApiModels.ProductRequest.class);
                ReadOnlyProduct product = productService.saveProduct(request.name(), request.barCode(),
                        request.description(), request.categoryId(), request.unit());
                exchange.getResponseHeaders().set("Location", "/api/products/" + product.getId());
                HttpResponseWriter.sendJson(exchange, HttpStatus.CREATED.getCode(), ApiModels.ProductResponse.from(product));
                return;
            }
            HttpRequests.requireMethod(exchange, "GET", "POST");
        }
        if (path.length == 2 && path[1].equals("summary")) {
            HttpRequests.requireMethod(exchange, "GET");
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    productService.getInventorySummary().stream().map(ApiModels.ProductInventoryResponse::from).toList());
            return;
        }
        if (path.length == 3 && path[1].equals("barcode")) {
            HttpRequests.requireMethod(exchange, "GET");
            ReadOnlyProduct product = productService.findByBarCode(path[2]);
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(), ApiModels.ProductResponse.from(product));
            return;
        }
        if (path.length == 2) {
            UUID id = HttpRequests.uuid(path[1]);
            if ("GET".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "GET");
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(), ApiModels.ProductResponse.from(productService.findById(id)));
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "PUT");
                ApiModels.ProductRequest request = HttpRequests.readJson(exchange, ApiModels.ProductRequest.class);
                ProductDTO product = new ProductDTO(request.name(), request.barCode(), request.description(),
                        request.categoryId(), request.unit());
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                        ApiModels.ProductResponse.from(productService.editProduct(id, product)));
                return;
            }
            HttpRequests.requireMethod(exchange, "GET", "PUT");
        }
        if (path.length == 3 && path[2].equals("lots")) {
            HttpRequests.requireMethod(exchange, "GET");
            UUID id = HttpRequests.uuid(path[1]);
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    lotService.findByProductId(id).stream().map(ApiModels.LotResponse::from).toList());
            return;
        }
        if (path.length == 3 && path[2].equals("stock")) {
            HttpRequests.requireMethod(exchange, "GET");
            UUID id = HttpRequests.uuid(path[1]);
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    new ApiModels.StockResponse(id, lotService.getTotalAvailableQuantity(id)));
            return;
        }
        if (path.length > 0 && path[0].equals("products")) {
            throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
        }
        throw new ApiException(404, "NOT_FOUND", "Rota não encontrada.");
    }
}
