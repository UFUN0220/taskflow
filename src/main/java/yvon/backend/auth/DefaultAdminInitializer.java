package yvon.backend.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "taskflow.auth.bootstrap-admin.enabled", havingValue = "true")
public class DefaultAdminInitializer implements ApplicationRunner {

    private final AuthProperties properties;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminInitializer(AuthProperties properties, SysUserMapper userMapper,
                                   JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin config = properties.getBootstrapAdmin();
        if (config.getPassword() == null || config.getPassword().isBlank()) {
            throw new IllegalStateException("Bootstrap admin is enabled but TASKFLOW_BOOTSTRAP_ADMIN_PASSWORD is blank");
        }

        SysUserEntity user = userMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getUsername, config.getUsername())
                .last("LIMIT 1"));
        if (user == null) {
            user = new SysUserEntity();
            user.setUsername(config.getUsername());
            user.setEmployeeNo(config.getEmployeeNo());
            user.setDisplayName(config.getDisplayName());
            user.setPasswordHash(passwordEncoder.encode(config.getPassword()));
            user.setStatus("ACTIVE");
            userMapper.insert(user);
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
