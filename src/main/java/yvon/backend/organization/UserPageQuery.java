package yvon.backend.organization;

public record UserPageQuery(long page, long size, Long departmentId) {
    public UserPageQuery {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
    }
}
