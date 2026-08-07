package yvon.backend;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.common.error.GlobalExceptionHandler;
import yvon.backend.common.trace.TraceIdFilter;
import yvon.backend.bootstrap.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import yvon.backend.bootstrap.HealthService;

@WebMvcTest(properties = "taskflow.auth.enabled=false")
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class,
        GlobalExceptionHandlerTest.ProbeController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthService healthService;

    @Test
    void invalidParameterReturnsUnifiedErrorAndTraceId() throws Exception {
        mockMvc.perform(get("/test-probe/validate")
                        .param("name", "x")
                        .header(TraceIdFilter.HEADER_NAME, "trace-test-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.traceId").value("trace-test-001"))
                .andExpect(header().string(TraceIdFilter.HEADER_NAME, "trace-test-001"));
    }

    @Test
    void businessExceptionReturnsStableCode() throws Exception {
        mockMvc.perform(get("/test-probe/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_400"))
                .andExpect(jsonPath("$.message").value("示例业务异常"));
    }

    @Test
    void unknownExceptionDoesNotExposeStackTrace() throws Exception {
        mockMvc.perform(get("/test-probe/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_500"))
                .andExpect(jsonPath("$.message").value("系统内部错误"));
    }

    @RestController
    @Validated
    static class ProbeController {

        @GetMapping("/test-probe/validate")
        String validate(@RequestParam @Size(min = 3, max = 20) String name) {
            return name;
        }

        @GetMapping("/test-probe/business")
        String business() {
            throw new BusinessException(BusinessErrorCode.BUSINESS_ERROR, "示例业务异常");
        }

        @GetMapping("/test-probe/unknown")
        String unknown() {
            throw new IllegalStateException("internal detail must not be exposed");
        }
    }
}
