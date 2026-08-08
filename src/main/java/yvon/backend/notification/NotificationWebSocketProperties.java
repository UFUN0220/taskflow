package yvon.backend.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "taskflow.websocket")
public class NotificationWebSocketProperties {

    private boolean enabled = true;
    private String endpoint = "/ws/notifications";
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173", "http://127.0.0.1:5173"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
