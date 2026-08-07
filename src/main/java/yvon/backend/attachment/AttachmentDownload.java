package yvon.backend.attachment;

import java.io.InputStream;

public record AttachmentDownload(InputStream inputStream, String originalFilename, String contentType, long sizeBytes) {
}
