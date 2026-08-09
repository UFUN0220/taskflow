package yvon.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Credentials and metadata supplied only to the isolated acceptance profile. */
@Validated
@ConfigurationProperties(prefix = "taskflow.acceptance")
public class AcceptanceProperties {

    private boolean enabled;

    @Valid
    private final Admin admin = new Admin();

    @NotBlank
    @Size(min = 12, max = 72)
    private String testUserPassword;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Admin getAdmin() {
        return admin;
    }

    public String getTestUserPassword() {
        return testUserPassword;
    }

    public void setTestUserPassword(String testUserPassword) {
        this.testUserPassword = testUserPassword;
    }

    public static class Admin {
        @NotBlank
        @Size(max = 64)
        private String username;

        @NotBlank
        @Size(min = 12, max = 72)
        private String password;

        @NotBlank
        @Size(max = 64)
        private String employeeNo = "ACCEPTANCE001";

        @NotBlank
        @Size(max = 128)
        private String displayName = "Acceptance Administrator";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
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
    }
}
