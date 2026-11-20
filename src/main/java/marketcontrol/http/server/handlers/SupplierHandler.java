package marketcontrol.http.server.handlers;

import java.io.IOException;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.domain.model.interfaces.ReadOnlySupplier;
import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.HttpRequests;
import marketcontrol.http.server.responses.ApiModels;
import marketcontrol.http.server.responses.HttpResponseWriter;
import marketcontrol.http.server.status.HttpStatus;
import marketcontrol.service.SupplierService;

public class SupplierHandler implements HttpHandler {
    private final SupplierService supplierService;

    public SupplierHandler(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 1) {
            HttpRequests.requireMethod(exchange, "POST");
            ApiModels.SupplierRequest request = HttpRequests.readJson(exchange, ApiModels.SupplierRequest.class);
            ReadOnlySupplier supplier = supplierService.save(request.legalName(), request.tradeName(), request.document(),
                    request.email(), request.phone(), request.contactName());
            exchange.getResponseHeaders().set("Location", "/api/suppliers/" + supplier.getId());
            HttpResponseWriter.sendJson(exchange, HttpStatus.CREATED.getCode(), ApiModels.SupplierResponse.from(supplier));
            return;
        }
        if (path.length == 2) {
            UUID id = HttpRequests.uuid(path[1]);
            if ("GET".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "GET");
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                        ApiModels.SupplierResponse.from(supplierService.findById(id)));
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod())) {
                HttpRequests.requireMethod(exchange, "PUT");
                ApiModels.SupplierRequest request = HttpRequests.readJson(exchange, ApiModels.SupplierRequest.class);
                ReadOnlySupplier supplier = supplierService.update(id, request.legalName(), request.tradeName(),
                        request.document(), request.email(), request.phone(), request.contactName());
                HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(), ApiModels.SupplierResponse.from(supplier));
                return;
            }
            HttpRequests.requireMethod(exchange, "GET", "PUT");
        }
        if (path.length == 3 && path[2].equals("deactivation")) {
            HttpRequests.requireMethod(exchange, "POST");
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    ApiModels.SupplierResponse.from(supplierService.deactivate(HttpRequests.uuid(path[1]))));
            return;
        }
        throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
    }
}
