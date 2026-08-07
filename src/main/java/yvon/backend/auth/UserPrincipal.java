package yvon.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public final class UserPrincipal implements UserDetails {

    private final SysUserEntity user;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(SysUserEntity user, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    public Long userId() { return user.getId(); }
    public String displayName() { return user.getDisplayName(); }
    public Long departmentId() { return user.getDepartmentId(); }
    public SysUserEntity user() { return user; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !"LOCKED".equals(user.getStatus()); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return "ACTIVE".equals(user.getStatus()); }
}
