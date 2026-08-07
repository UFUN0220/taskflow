package yvon.backend.attachment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yvon.backend.auth.UserPrincipal;
import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.error.BusinessException;
import yvon.backend.organization.PageResponse;
import yvon.backend.task.TaskEntity;
import yvon.backend.task.TaskService;

import java.io.InputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "taskflow.attachment.enabled", havingValue = "true")
public class TaskAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(TaskAttachmentService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "txt");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.APPLICATION_PDF_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE,
            MediaType.TEXT_PLAIN_VALUE);

    private final TaskAttachmentMetadataService metadataService;
    private final MinioObjectStorage storage;
    private final TaskService taskService;
    private final AttachmentProperties properties;

    public TaskAttachmentService(TaskAttachmentMetadataService metadataService, MinioObjectStorage storage,
                                 TaskService taskService, AttachmentProperties properties) {
        this.metadataService = metadataService;
        this.storage = storage;
        this.taskService = taskService;
        this.properties = properties;
    }

    public TaskAttachmentResponse upload(Long taskId, MultipartFile file, UserPrincipal principal) {
        taskService.requireVisible(taskId, principal);
        ValidatedFile validated = validate(file);
        String objectKey = "tasks/" + taskId + "/" + UUID.randomUUID() + "." + validated.extension();

        TaskAttachmentEntity attachment = new TaskAttachmentEntity();
        attachment.setTaskId(taskId);
        attachment.setUploaderUserId(principal.userId());
        attachment.setStorageBucket(properties.getMinio().getBucket());
        attachment.setObjectKey(objectKey);
        attachment.setOriginalFilename(validated.originalFilename());
        attachment.setContentType(validated.contentType());
        attachment.setSizeBytes((long) validated.content().length);
        attachment.setChecksum(validated.checksum());
        attachment = metadataService.createPending(attachment);

        try {
            storage.put(objectKey, validated.content(), validated.contentType());
        } catch (Exception exception) {
            markUploadFailed(attachment, principal.userId());
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR, "附件上传失败，请稍后重试");
        }

        try {
            return TaskAttachmentResponse.from(metadataService.markAvailable(attachment, principal.userId()));
        } catch (RuntimeException exception) {
            compensateObject(objectKey, attachment.getId());
            markUploadFailed(attachment, principal.userId());
            throw exception;
        }
    }

    public PageResponse<TaskAttachmentResponse> page(Long taskId, long page, long size, UserPrincipal principal) {
        taskService.requireVisible(taskId, principal);
        PageResponse<TaskAttachmentEntity> result = metadataService.page(taskId, page, size);
        return new PageResponse<>(result.records().stream().map(TaskAttachmentResponse::from).toList(),
                result.total(), result.current(), result.size(), result.pages());
    }

    public AttachmentDownload openDownload(Long taskId, Long attachmentId, UserPrincipal principal) {
        TaskAttachmentEntity attachment = requireAccessible(taskId, attachmentId, principal);
        try {
            InputStream inputStream = storage.get(attachment.getObjectKey());
            return new AttachmentDownload(inputStream, attachment.getOriginalFilename(),
                    attachment.getContentType(), attachment.getSizeBytes());
        } catch (Exception exception) {
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR, "附件读取失败，请稍后重试");
        }
    }

    public String presignedUrl(Long taskId, Long attachmentId, UserPrincipal principal) {
        TaskAttachmentEntity attachment = requireAccessible(taskId, attachmentId, principal);
        try {
            return storage.presignedGet(attachment.getObjectKey());
        } catch (Exception exception) {
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR, "附件临时地址生成失败，请稍后重试");
        }
    }

    public void delete(Long taskId, Long attachmentId, UserPrincipal principal) {
        TaskAttachmentEntity attachment = requireAccessible(taskId, attachmentId, principal);
        TaskEntity task = taskService.requireVisible(attachment.getTaskId(), principal);
        if (!attachment.getUploaderUserId().equals(principal.userId())
                && !task.getCreatorId().equals(principal.userId())
                && !hasAuthority(principal, "task:attachment:delete")) {
            throw new BusinessException(BusinessErrorCode.FORBIDDEN, "无权删除该附件");
        }

        TaskAttachmentEntity deleted = metadataService.markDeleted(attachment, principal.userId());
        try {
            storage.remove(deleted.getObjectKey());
        } catch (Exception exception) {
            try {
                metadataService.markCleanupFailed(deleted, principal.userId());
            } catch (RuntimeException compensationException) {
                log.error("Attachment cleanup state could not be recorded, attachmentId={}", attachmentId,
                        compensationException);
            }
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR, "附件删除正在等待补偿，请稍后重试");
        }
    }

    private TaskAttachmentEntity requireAccessible(Long taskId, Long attachmentId, UserPrincipal principal) {
        TaskAttachmentEntity attachment = metadataService.requireAvailable(attachmentId);
        if (!attachment.getTaskId().equals(taskId)) {
            throw new BusinessException(BusinessErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
        }
        taskService.requireVisible(attachment.getTaskId(), principal);
        return attachment;
    }


    private void markUploadFailed(TaskAttachmentEntity attachment, Long operatorId) {
        try {
            metadataService.markFailed(attachment, operatorId);
        } catch (RuntimeException compensationException) {
            log.error("Attachment upload failure state could not be recorded, attachmentId={}", attachment.getId(),
                    compensationException);
        }
    }

    private void compensateObject(String objectKey, Long attachmentId) {
        try {
            storage.remove(objectKey);
        } catch (Exception cleanupException) {
            log.error("Orphan attachment cleanup failed, attachmentId={}", attachmentId, cleanupException);
        }
    }

    private boolean hasAuthority(UserPrincipal principal, String authority) {
        return principal.getAuthorities().stream().anyMatch(item -> authority.equals(item.getAuthority()));
    }

    private ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件大小超过限制");
        }
        String originalFilename = safeFilename(file.getOriginalFilename());
        String extension = extension(originalFilename);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension) || !ALLOWED_CONTENT_TYPES.contains(contentType)
                || !contentTypeMatchesExtension(contentType, extension)) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件类型或后缀不受支持");
        }
        try {
            byte[] content = file.getBytes();
            return new ValidatedFile(originalFilename, extension, contentType, content, sha256(content));
        } catch (Exception exception) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件内容读取失败");
        }
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件文件名不能为空");
        }
        String safe = Paths.get(filename).getFileName().toString()
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (safe.isBlank() || safe.length() > 255 || safe.equals(".") || safe.equals("..")) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件文件名不合法");
        }
        return safe;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw new BusinessException(BusinessErrorCode.INVALID_PARAMETER, "附件必须包含合法后缀");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean contentTypeMatchesExtension(String contentType, String extension) {
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE.equals(contentType);
            case "png" -> MediaType.IMAGE_PNG_VALUE.equals(contentType);
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE.equals(contentType);
            case "txt" -> MediaType.TEXT_PLAIN_VALUE.equals(contentType);
            default -> false;
        };
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedFile(String originalFilename, String extension, String contentType,
                                 byte[] content, String checksum) {
    }
}
