package yvon.backend.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates or reconciles only the administrator used by the isolated acceptance
 * profile. It is deliberately profile-bound and never participates in dev/prod.
 */
@Component
@Profile("acceptance")
@EnableConfigurationProperties(AcceptanceProperties.class)
public class AcceptanceAdminInitializer implements ApplicationRunner {

    private final AcceptanceProperties properties;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AcceptanceAdminInitializer(AcceptanceProperties properties, SysUserMapper userMapper,
                                      JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Acceptance profile requires taskflow.acceptance.enabled=true");
        }

        AcceptanceProperties.Admin config = properties.getAdmin();
        SysUserEntity user = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, config.getUsername())
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUserEntity();
            user.setUsername(config.getUsername());
            user.setPasswordHash(passwordEncoder.encode(config.getPassword()));
            user.setStatus("ACTIVE");
            user.setEmployeeNo(config.getEmployeeNo());
            user.setDisplayName(config.getDisplayName());
            userMapper.insert(user);
        } else {
            // Reconcile only the isolated acceptance account so reruns do not
            // depend on a password left in a persistent local database.
            user.setPasswordHash(passwordEncoder.encode(config.getPassword()));
            user.setStatus("ACTIVE");
            user.setEmployeeNo(config.getEmployeeNo());
            user.setDisplayName(config.getDisplayName());
            userMapper.updateById(user);
        }

        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = 'system_admin' AND deleted = 0", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """, user.getId(), roleId);
    }
}
