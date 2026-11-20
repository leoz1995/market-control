package marketcontrol.http.server.responses;

import java.time.Instant;

public record ApiError(int status, String error, String message, String path, String requestId, Instant timestamp) {}
