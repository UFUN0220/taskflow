package yvon.backend;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import yvon.backend.attachment.*;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.error.BusinessException;
import yvon.backend.task.TaskService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskAttachmentServiceTest {

    private final TaskAttachmentMetadataService metadataService = mock(TaskAttachmentMetadataService.class);
    private final MinioObjectStorage storage = mock(MinioObjectStorage.class);
    private final TaskService taskService = mock(TaskService.class);
    private final AttachmentProperties properties = properties();
    private final TaskAttachmentService service = new TaskAttachmentService(metadataService, storage, taskService, properties);

    @Test
    void oversizedFileIsRejectedBeforeMetadataOrMinioCalls() {
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", new byte[11]);
        properties.setMaxFileSizeBytes(10);

        assertThatThrownBy(() -> service.upload(9L, file, TestFixtures.principal()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小超过限制");

        verifyNoInteractions(metadataService, storage);
    }

    @Test
    void pathTraversalNameIsReducedToSafeBaseNameBeforeObjectKeyGeneration() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "..\\secret.txt", "text/plain", "hello".getBytes());
        TaskAttachmentEntity pending = attachment(21L, 0, "UPLOADING");
        TaskAttachmentEntity available = attachment(21L, 1, "AVAILABLE");
        when(metadataService.createPending(any(TaskAttachmentEntity.class))).thenReturn(pending);
        when(metadataService.markAvailable(pending, 7L)).thenReturn(available);

        service.upload(9L, file, TestFixtures.principal());

        verify(storage).put(argThat(key -> key.startsWith("tasks/9/") && !key.contains("secret.txt")),
                any(byte[].class), eq("text/plain"));
        verify(metadataService).createPending(argThat(entity -> "secret.txt".equals(entity.getOriginalFilename())));
    }

    @Test
    void minioFailureMarksMetadataAsFailedAndDoesNotExposeStorageDetails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        TaskAttachmentEntity pending = attachment(22L, 0, "UPLOADING");
        when(metadataService.createPending(any(TaskAttachmentEntity.class))).thenReturn(pending);
        doThrow(new IllegalStateException("storage detail")).when(storage)
                .put(anyString(), any(byte[].class), anyString());

        assertThatThrownBy(() -> service.upload(9L, file, TestFixtures.principal()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("附件上传失败，请稍后重试");

        verify(metadataService).markFailed(pending, 7L);
    }

    @Test
    void attachmentFromAnotherTaskIsNotAccessibleEvenWhenUserCanSeeBothTasks() {
        TaskAttachmentEntity available = attachment(23L, 1, "AVAILABLE");
        when(metadataService.requireAvailable(23L)).thenReturn(available);

        assertThatThrownBy(() -> service.openDownload(10L, 23L, TestFixtures.principal()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("附件不存在");

        verifyNoInteractions(storage);
    }

    private AttachmentProperties properties() {
        AttachmentProperties result = new AttachmentProperties();
        result.getMinio().setBucket("taskflow-attachments");
        result.setMaxFileSizeBytes(1024);
        return result;
    }

    private TaskAttachmentEntity attachment(Long id, int version, String status) {
        TaskAttachmentEntity entity = new TaskAttachmentEntity();
        entity.setId(id);
        entity.setVersion(version);
        entity.setStatus(status);
        entity.setTaskId(9L);
        entity.setOriginalFilename("note.txt");
        entity.setContentType("text/plain");
        entity.setSizeBytes(5L);
        entity.setObjectKey("tasks/9/object");
        return entity;
    }
}
