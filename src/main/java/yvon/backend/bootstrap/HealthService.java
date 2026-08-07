package yvon.backend.bootstrap;

import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public HealthInfo current() {
        return new HealthInfo("UP", "taskflow-backend");
    }

    public record HealthInfo(String status, String service) {
    }
}
