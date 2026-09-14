package com.photoshare.photo.storage;

import com.cloudinary.Cloudinary;
import com.photoshare.config.PhotoShareProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

/** Explicit developer command, compiled outside application sources; no web server or database startup. */
public final class CloudinarySmoke {
    public static void main(String[] args) {
        int result;
        try {
            result = run(args);
        } catch (Exception exception) {
            // Binding exceptions can include rejected configuration values: never print their messages/stack.
            System.out.println("SMOKE result=FAIL stage=configuration_or_fixture exceptionClass=" + exception.getClass().getName());
            result = 1;
        }
        System.exit(result);
    }

    private static int run(String[] args) throws Exception {
        if (args.length > 1 || (args.length == 1
                && !args[0].matches("events/[0-9]+/photos/[a-zA-Z0-9_-]+\\.(jpg|png)"))) {
            System.out.println("SMOKE result=FAIL reason=invalid_existing_storage_key");
            return 1;
        }
        StandardEnvironment environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("photoshare", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        // Bind only storage: normal application.yml and the launching shell, without DB/JWT/test defaults.
        var storage = Binder.get(environment).bind("photoshare.storage",
                Bindable.of(PhotoShareProperties.Storage.class)).orElseThrow(IllegalStateException::new);
        if (!"cloudinary".equals(storage.driver())) {
            System.out.println("SMOKE result=FAIL reason=PHOTOSHARE_STORAGE_DRIVER_must_be_cloudinary");
            return 1;
        }
        var credentials = storage.cloudinary();
        reportValue("cloudName", credentials == null ? null : credentials.cloudName(), false);
        reportValue("apiKey", credentials == null ? null : credentials.apiKey(), true);
        reportValue("apiSecret", credentials == null ? null : credentials.apiSecret(), true);
        var properties = new PhotoShareProperties(null, null, null, storage, null);
        var factory = new CloudinaryStorageConfiguration();
        Cloudinary cloudinary = factory.cloudinary(properties);
        var diagnostics = new CloudinaryDiagnostics(cloudinary);
        var client = factory.cloudinaryAssetClient(cloudinary);
        var adapter = factory.cloudinaryPhotoStorage(client, properties);

        boolean pingSucceeded;
        try {
            // SDK Api.ping performs authenticated GET /v1_1/<cloud>/ping using HTTP Basic auth.
            var response = cloudinary.api().ping(Map.of("timeout", 30000));
            pingSucceeded = "ok".equals(response.get("status"));
            System.out.println("SMOKE ping=" + (pingSucceeded ? "SUCCESS" : "FAIL") + " authentication=HTTP_BASIC");
        } catch (Exception exception) {
            diagnostics.exceptionFailure("ping", "admin_api", exception);
            System.out.println("SMOKE ping=FAIL");
            pingSucceeded = false;
        }

        byte[] png = fixture();
        SmokeResult roundTrip = uploadReadOnce(adapter, png);
        boolean existingSucceeded = args.length == 0 || readExisting(adapter, args[0]);
        boolean success = roundTrip.success() && pingSucceeded && existingSucceeded;
        System.out.println("SMOKE result=" + (success ? "SUCCESS" : "FAIL")
                + " ping=" + (pingSucceeded ? "SUCCESS" : "FAIL")
                + " upload=" + roundTrip.upload() + " read=" + roundTrip.read() + " cleanup=" + roundTrip.cleanup());
        return success ? 0 : 1;
    }

    private static void reportValue(String name, String value, boolean includeLength) {
        boolean present = value != null && !value.isBlank();
        boolean whitespace = value != null && !value.equals(value.strip());
        System.out.println("SMOKE config field=" + name + " present=" + present
                + " edgeWhitespace=" + whitespace + (includeLength ? " length=" + (value == null ? 0 : value.length()) : ""));
    }

    private static SmokeResult uploadReadOnce(PhotoStorage adapter, byte[] png) {
        String key = "events/0/photos/smoke_" + UUID.randomUUID() + ".png";
        boolean cleanup = false;
        String uploadResult = "FAIL";
        String readResult = "SKIPPED";
        String cleanupResult = "SKIPPED";
        try {
            PhotoStorage.StoredPhoto stored = adapter.put(key, png, "image/png", "photoshare-smoke.png");
            cleanup = true;
            uploadResult = "SUCCESS";
            System.out.println("SMOKE upload=SUCCESS adapter=production resourceType=image deliveryType=authenticated");
            try {
                // Use returned metadata, exactly as the application does. No URL is exposed.
                verifyImage(adapter, stored.storageKey(), stored.contentType());
                readResult = "SUCCESS";
                System.out.println("SMOKE read=SUCCESS adapter=production format=png contentType=image/png nonEmpty=true");
            } catch (Exception exception) {
                readResult = "FAIL";
                System.out.println("SMOKE read=FAIL exceptionClass=" + exception.getClass().getName());
            }
        } catch (StorageException exception) {
            cleanup = exception.cleanupAllowed();
            System.out.println("SMOKE upload=FAIL rootType=" + exception.rootCauseType());
        } finally {
            if (cleanup) {
                try {
                    adapter.delete(key);
                    cleanupResult = "SUCCESS";
                    System.out.println("SMOKE cleanup=SUCCESS");
                } catch (StorageException exception) {
                    System.out.println("SMOKE cleanup=FAIL diagnosticAsset=" + key);
                    cleanupResult = "FAIL";
                }
            } else {
                System.out.println("SMOKE cleanup=SKIPPED reason=no_confirmed_asset outcome_may_be_unknown");
            }
        }
        return new SmokeResult(uploadResult, readResult, cleanupResult);
    }

    private static boolean readExisting(PhotoStorage adapter, String key) {
        try {
            verifyImage(adapter, key, key.endsWith(".png") ? "image/png" : "image/jpeg");
            System.out.println("SMOKE existingRead=SUCCESS mode=read_only cleanup=NOT_APPLICABLE");
            return true;
        } catch (Exception exception) {
            System.out.println("SMOKE existingRead=FAIL mode=read_only exceptionClass=" + exception.getClass().getName());
            return false;
        }
    }

    private static void verifyImage(PhotoStorage adapter, String key, String contentType) throws Exception {
        byte[] bytes = adapter.read(key).getContentAsByteArray();
        if (bytes.length == 0) throw new IllegalStateException("Empty photo");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalStateException("Unreadable image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                boolean png = "png".equalsIgnoreCase(format) && "image/png".equals(contentType);
                boolean jpeg = ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format))
                        && "image/jpeg".equals(contentType);
                if (!(png || jpeg) || reader.read(0) == null) throw new IllegalStateException("Image format mismatch");
            } finally {
                reader.dispose();
            }
        }
    }

    private record SmokeResult(String upload, String read, String cleanup) {
        boolean success() {
            return "SUCCESS".equals(upload) && "SUCCESS".equals(read) && "SUCCESS".equals(cleanup);
        }
    }

    private static byte[] fixture() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}
