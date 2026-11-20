package marketcontrol.http.server.handlers;

import java.io.IOException;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.HttpRequests;
import marketcontrol.http.server.responses.ApiModels;
import marketcontrol.http.server.responses.HttpResponseWriter;
import marketcontrol.http.server.status.HttpStatus;
import marketcontrol.service.LotService;
import marketcontrol.service.StockMovementService;

public class LotHandler implements HttpHandler {
    private final LotService lotService;
    private final StockMovementService movementService;

    public LotHandler(LotService lotService, StockMovementService movementService) {
        this.lotService = lotService;
        this.movementService = movementService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String[] path = HttpRequests.segments(exchange);
        if (path.length == 1) {
            HttpRequests.requireMethod(exchange, "POST");
            ApiModels.LotRequest request = HttpRequests.readJson(exchange, ApiModels.LotRequest.class);
            var lot = lotService.save(request.productId(), request.supplierId(), request.entryDate(),
                    request.expirationDate(), request.initialQuantity(), request.unitCost());
            exchange.getResponseHeaders().set("Location", "/api/lots/" + lot.getId());
            HttpResponseWriter.sendJson(exchange, HttpStatus.CREATED.getCode(), ApiModels.LotResponse.from(lot));
            return;
        }
        if (path.length == 2) {
            HttpRequests.requireMethod(exchange, "GET");
            HttpResponseWriter.sendJson(exchange, HttpStatus.OK.getCode(),
                    ApiModels.LotResponse.from(lotService.findById(HttpRequests.uuid(path[1]))));
            return;
        }
        if (path.length == 3 && (path[2].equals("sales") || path[2].equals("losses"))) {
            HttpRequests.requireMethod(exchange, "POST");
            UUID lotId = HttpRequests.uuid(path[1]);
            ApiModels.MovementRequest request = HttpRequests.readJson(exchange, ApiModels.MovementRequest.class);
            if (path[2].equals("sales")) {
                movementService.registerSale(lotId, request.quantity());
            } else {
                movementService.registerLoss(lotId, request.quantity(), request.cause());
            }
            HttpResponseWriter.sendEmpty(exchange, HttpStatus.NO_CONTENT.getCode());
            return;
        }
        throw new ApiException(404, "NOT_FOUND", "Rota ou recurso não encontrado.");
    }
}
