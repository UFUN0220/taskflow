package yvon.backend;

import org.junit.jupiter.api.Test;
import yvon.backend.organization.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DepartmentServiceTest {

    @Test
    void createRootDepartmentBuildsStablePath() {
        SysDepartmentMapper mapper = mock(SysDepartmentMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SysDepartmentEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            entity.setVersion(0);
            return 1;
        }).when(mapper).insert(any(SysDepartmentEntity.class));
        DepartmentService service = new DepartmentService(mapper);

        DepartmentResponse response = service.create(new CreateDepartmentRequest("eng", "研发部", null));

        assertThat(response.path()).isEqualTo("/eng/");
        assertThat(response.level()).isEqualTo(1);
        verify(mapper).insert(any(SysDepartmentEntity.class));
    }
}
