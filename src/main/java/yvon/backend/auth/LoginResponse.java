package yvon.backend.auth;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        Long userId,
        String username,
        String displayName
) {
}
