package yvon.backend.organization;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        long current,
        long size,
        long pages
) {
}
