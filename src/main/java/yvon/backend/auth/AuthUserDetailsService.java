package yvon.backend.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "taskflow.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;

    public AuthUserDetailsService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        SysUserEntity user = userMapper.findActiveByLogin(login);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        if ("LOCKED".equals(user.getStatus())) {
            throw new LockedException("User is locked");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new DisabledException("User is disabled");
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        userMapper.findRoleCodes(user.getId()).forEach(role ->
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        userMapper.findPermissionCodes(user.getId()).forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission)));
        return new UserPrincipal(user, authorities);
    }
}
