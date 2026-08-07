package yvon.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import yvon.backend.common.api.ApiResponse;
import yvon.backend.common.error.BusinessErrorCode;

import java.io.IOException;

@Component
public class ApiSecurityResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiSecurityResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, BusinessErrorCode errorCode) throws IOException {
        response.setStatus(errorCode == BusinessErrorCode.UNAUTHORIZED ? 401 : 403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(errorCode, null));
    }
}
