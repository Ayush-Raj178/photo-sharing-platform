package com.photoshare.photo;

import com.photoshare.common.ApiException;
import com.photoshare.config.PhotoShareProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

@Component
public class PhotoValidation {
    private final PhotoShareProperties properties;

    public PhotoValidation(PhotoShareProperties properties) {
        this.properties = properties;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("INVALID_IMAGE", "Image file is empty");
        }
        if (file.getSize() > properties.storage().maxFileSizeBytes()) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Image exceeds the allowed size");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = extension(filename);
        String claimedType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!(extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png"))
                || !(claimedType.equals("image/jpeg") || claimedType.equals("image/png"))) {
            throw ApiException.badRequest("UNSUPPORTED_IMAGE_TYPE", "Only JPEG and PNG images are accepted");
        }

        try {
            byte[] bytes = file.getBytes();
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                if (input == null) throw new IOException("No image input");
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw ApiException.badRequest("INVALID_IMAGE", "Image content is invalid");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    long pixels = Math.multiplyExact((long) width, (long) height);
                    if (width <= 0 || height <= 0 || pixels > properties.storage().maxImagePixels()) {
                        throw ApiException.badRequest("IMAGE_DIMENSIONS_EXCEEDED",
                                "Image dimensions are not allowed");
                    }
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String actualType = format.contains("png") ? "image/png"
                            : (format.contains("jpeg") || format.contains("jpg")) ? "image/jpeg" : "";
                    if (actualType.isEmpty() || !actualType.equals(claimedType)
                            || (actualType.equals("image/png") && !extension.equals("png"))
                            || (actualType.equals("image/jpeg")
                                && !(extension.equals("jpg") || extension.equals("jpeg")))) {
                        throw ApiException.badRequest("UNSUPPORTED_IMAGE_TYPE",
                                "Image extension, type, and content must match");
                    }
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) {
                        throw ApiException.badRequest("INVALID_IMAGE", "Image content is invalid");
                    }
                    return new ValidatedImage(filename, extension.equals("jpeg") ? "jpg" : extension,
                            actualType, width, height, bytes);
                } finally {
                    reader.dispose();
                }
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | ArithmeticException ex) {
            throw ApiException.badRequest("INVALID_IMAGE", "Image content is invalid");
        }
    }

    private String sanitizeFilename(String original) {
        String value = original == null ? "image" : original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isEmpty()) value = "image";
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record ValidatedImage(String filename, String extension, String contentType,
                                 int width, int height, byte[] bytes) {}
}

