package yvon.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskflow.auth")
public class AuthProperties {

    @NotBlank
    @Size(min = 32)
    private String jwtSecret;

    @Min(1)
    private long jwtExpirationMinutes = 120;

    private boolean loginRateLimitEnabled = true;

    @Min(1)
    private int loginRateLimitMaxAttempts = 10;

    @Min(1)
    private long loginRateLimitWindowSeconds = 60;

    @Valid
    private final BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();

    @Valid
    private final BrowserCookie browserCookie = new BrowserCookie();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getJwtExpirationMinutes() {
        return jwtExpirationMinutes;
    }

    public void setJwtExpirationMinutes(long jwtExpirationMinutes) {
        this.jwtExpirationMinutes = jwtExpirationMinutes;
    }

    public boolean isLoginRateLimitEnabled() {
        return loginRateLimitEnabled;
    }

    public void setLoginRateLimitEnabled(boolean loginRateLimitEnabled) {
        this.loginRateLimitEnabled = loginRateLimitEnabled;
    }

    public int getLoginRateLimitMaxAttempts() {
        return loginRateLimitMaxAttempts;
    }

    public void setLoginRateLimitMaxAttempts(int loginRateLimitMaxAttempts) {
        this.loginRateLimitMaxAttempts = loginRateLimitMaxAttempts;
    }

    public long getLoginRateLimitWindowSeconds() {
        return loginRateLimitWindowSeconds;
    }

    public void setLoginRateLimitWindowSeconds(long loginRateLimitWindowSeconds) {
        this.loginRateLimitWindowSeconds = loginRateLimitWindowSeconds;
    }

    public BootstrapAdmin getBootstrapAdmin() {
        return bootstrapAdmin;
    }

    public BrowserCookie getBrowserCookie() {
        return browserCookie;
    }

    public static class BootstrapAdmin {
        private boolean enabled;
        private String username = "admin";
        private String employeeNo = "ADMIN001";
        private String displayName = "System Administrator";
        private String password;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmployeeNo() {
            return employeeNo;
        }

        public void setEmployeeNo(String employeeNo) {
            this.employeeNo = employeeNo;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class BrowserCookie {
        private boolean enabled = true;
        private String name = "TASKFLOW_ACCESS";
        private String path = "/";
        private boolean httpOnly = true;
        private boolean secure;
        private String sameSite = "Lax";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isHttpOnly() { return httpOnly; }
        public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    }
}
