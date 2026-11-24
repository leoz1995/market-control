package marketcontrol.dtos.auth;

import marketcontrol.dtos.UserResponse;

public record LoginResponse(String accessToken, String refreshToken, long expiresInSeconds,
                            String tokenType, UserResponse user) {}
