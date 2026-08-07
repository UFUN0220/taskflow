package yvon.backend.attachment;

import java.time.LocalDateTime;

public record TaskAttachmentResponse(
        Long attachmentId,
        Long taskId,
        Long uploaderUserId,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String checksum,
        String status,
        LocalDateTime createdAt
) {
    public static TaskAttachmentResponse from(TaskAttachmentEntity attachment) {
        return new TaskAttachmentResponse(attachment.getId(), attachment.getTaskId(), attachment.getUploaderUserId(),
                attachment.getOriginalFilename(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.getChecksum(), attachment.getStatus(), attachment.getCreatedAt());
    }
}
