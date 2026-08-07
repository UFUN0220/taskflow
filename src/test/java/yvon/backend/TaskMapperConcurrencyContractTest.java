package yvon.backend;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Update;
import yvon.backend.task.TaskMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TaskMapperConcurrencyContractTest {

    @Test
    void conditionalStateUpdateRequiresBothOldStatusAndVersion() {
        assertThat(TaskMapper.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("updateStatusWithVersion");
        var update = java.util.Arrays.stream(TaskMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("updateStatusWithVersion"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Update.class);

        assertThat(String.join(" ", update.value()))
                .contains("status = #{toStatus}")
                .contains("version = version + 1")
                .contains("status = #{fromStatus}")
                .contains("version = #{version}")
                .contains("deleted = 0");
    }
}
