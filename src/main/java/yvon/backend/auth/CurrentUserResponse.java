package yvon.backend.auth;

import java.util.List;

public record CurrentUserResponse(
        Long userId,
        String username,
        String employeeNo,
        String displayName,
        Long departmentId,
        List<String> authorities
) {
    public static CurrentUserResponse from(UserPrincipal principal) {
        return new CurrentUserResponse(
                principal.userId(), principal.getUsername(), principal.user().getEmployeeNo(),
                principal.displayName(), principal.departmentId(),
                principal.getAuthorities().stream().map(a -> a.getAuthority()).sorted().toList()
        );
    }
}
