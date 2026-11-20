package marketcontrol.http.server.handlers;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.time.Instant;
import java.util.UUID;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import marketcontrol.exception.ConflictException;
import marketcontrol.exception.PersistenceException;
import marketcontrol.exception.ResourceNotFoundException;
import marketcontrol.http.server.ApiException;
import marketcontrol.http.server.responses.ApiError;
import marketcontrol.http.server.responses.HttpResponseWriter;

public class ErrorHandlingHandler implements HttpHandler {
    private static final System.Logger LOGGER = System.getLogger(ErrorHandlingHandler.class.getName());
    private final HttpHandler delegate;

    public ErrorHandlingHandler(HttpHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set("X-Request-ID", requestId);
        try {
            delegate.handle(exchange);
        } catch (IOException ex) {
            LOGGER.log(System.Logger.Level.WARNING, "Falha de comunicação HTTP; requestId=" + requestId, ex);
            exchange.close();
        } catch (Exception ex) {
            if (exchange.getResponseCode() != -1) {
                LOGGER.log(System.Logger.Level.ERROR, "Falha após início da resposta; requestId=" + requestId, ex);
                exchange.close();
                return;
            }

            ErrorDetails details = classify(ex);
            if (details.status() >= 500) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Falha ao processar requisição; requestId=" + requestId + ", path=" + exchange.getRequestURI().getPath(), ex);
            } else {
                LOGGER.log(System.Logger.Level.INFO,
                        "Requisição rejeitada; requestId=" + requestId + ", status=" + details.status());
            }

            ApiError body = new ApiError(details.status(), details.code(), details.message(),
                    exchange.getRequestURI().getPath(), requestId, Instant.now());
            try {
                HttpResponseWriter.sendJson(exchange, details.status(), body);
            } catch (IOException responseException) {
                LOGGER.log(System.Logger.Level.ERROR, "Não foi possível enviar resposta de erro; requestId=" + requestId, responseException);
                exchange.close();
            }
        }
    }

    private static ErrorDetails classify(Exception exception) {
        if (exception instanceof ApiException apiException) {
            return new ErrorDetails(apiException.getStatus(), apiException.getCode(), apiException.getMessage());
        }
        if (exception instanceof ResourceNotFoundException) {
            return new ErrorDetails(404, "NOT_FOUND", exception.getMessage());
        }
        if (exception instanceof ConflictException) {
            return new ErrorDetails(409, "CONFLICT", exception.getMessage());
        }
        if (exception instanceof IllegalArgumentException) {
            return new ErrorDetails(400, "BAD_REQUEST", exception.getMessage());
        }
        if (hasCause(exception, SQLTransientException.class)) {
            return new ErrorDetails(503, "SERVICE_UNAVAILABLE", "O serviço está temporariamente indisponível.");
        }
        if (exception instanceof PersistenceException || hasCause(exception, SQLException.class)) {
            return new ErrorDetails(500, "PERSISTENCE_ERROR", "Não foi possível processar a operação.");
        }
        return new ErrorDetails(500, "INTERNAL_SERVER_ERROR", "Ocorreu um erro interno.");
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private record ErrorDetails(int status, String code, String message) {}
}
