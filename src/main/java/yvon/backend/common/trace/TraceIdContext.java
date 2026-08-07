package yvon.backend.common.trace;

import java.util.Optional;
import java.util.UUID;

public final class TraceIdContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdContext() {
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static Optional<String> current() {
        return Optional.ofNullable(TRACE_ID.get());
    }

    public static String getOrCreate() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            TRACE_ID.set(traceId);
        }
        return traceId;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
