package com.pizza.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pizza.exception.CloudinaryException;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles all pizza-image storage in Cloudinary. Only the secure URL and the
 * public id are ever returned to the rest of the application; images are never
 * stored on the local filesystem.
 */
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private static final String FOLDER = "pizza-ordering/pizzas";

    private final Cloudinary cloudinary;

    /**
     * Uploads an image and returns its details.
     *
     * @param file the multipart image file
     * @return the upload result (secure URL + public id)
     */
    public UploadResult upload(MultipartFile file) {
        validate(file);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", FOLDER, "resource_type", "image"));
            String url = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");
            log.debug("Uploaded image to Cloudinary: {}", publicId);
            return new UploadResult(url, publicId);
        } catch (IOException ex) {
            throw new CloudinaryException("Failed to upload image to Cloudinary", ex);
        }
    }

    /**
     * Replaces an existing image with a new one, deleting the old image.
     *
     * @param newFile     the replacement image
     * @param oldPublicId the public id of the image to remove (may be null)
     * @return the new upload result
     */
    public UploadResult replace(MultipartFile newFile, String oldPublicId) {
        UploadResult uploaded = upload(newFile);
        if (oldPublicId != null && !oldPublicId.isBlank()) {
            delete(oldPublicId);
        }
        return uploaded;
    }

    /**
     * Deletes an image from Cloudinary. Missing ids are ignored.
     *
     * @param publicId the Cloudinary public id
     */
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.debug("Deleted image from Cloudinary: {}", publicId);
        } catch (IOException ex) {
            throw new CloudinaryException("Failed to delete image from Cloudinary", ex);
        }
    }

    /**
     * Extracts the Cloudinary public id from a secure URL. Useful when only the
     * URL was persisted by a legacy record.
     *
     * @param url the secure Cloudinary URL
     * @return the public id, or null if it cannot be parsed
     */
    public String extractPublicId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx < 0) {
            return null;
        }
        String afterUpload = url.substring(uploadIdx + "/upload/".length());
        // Strip an optional version segment like "v1700000000/".
        if (afterUpload.matches("^v\\d+/.*")) {
            afterUpload = afterUpload.substring(afterUpload.indexOf('/') + 1);
        }
        int dotIdx = afterUpload.lastIndexOf('.');
        return dotIdx > 0 ? afterUpload.substring(0, dotIdx) : afterUpload;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CloudinaryException("Image file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CloudinaryException("Uploaded file must be an image");
        }
    }

    /** Immutable holder for a Cloudinary upload result. */
    public record UploadResult(String secureUrl, String publicId) {
    }
}
