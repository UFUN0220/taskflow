package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import yvon.backend.auth.AcceptanceAdminInitializer;
import yvon.backend.auth.DefaultAdminInitializer;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptanceEnvironmentTest {

    @Test
    void adminSeedersAreSeparatedByProfile() {
        assertThat(DefaultAdminInitializer.class.getAnnotation(Profile.class).value())
                .containsExactly("!acceptance");
        assertThat(AcceptanceAdminInitializer.class.getAnnotation(Profile.class).value())
                .containsExactly("acceptance");
    }

    @Test
    void acceptanceProfileHasNoPasswordFallback() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application-acceptance.properties"));
        assertThat(properties).contains("${TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD}");
        assertThat(properties).contains("${TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD}");
        assertThat(properties).doesNotContain("TASKFLOW_ACCEPTANCE_ADMIN_PASSWORD:");
        assertThat(properties).doesNotContain("TASKFLOW_ACCEPTANCE_TEST_USER_PASSWORD:");
    }
}
