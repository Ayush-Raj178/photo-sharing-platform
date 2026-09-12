package com.photoshare.photo.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.photoshare.config.PhotoShareProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "photoshare.storage", name = "driver", havingValue = "cloudinary")
public class CloudinaryStorageConfiguration {
    @Bean
    Cloudinary cloudinary(PhotoShareProperties properties) {
        PhotoShareProperties.Storage.Cloudinary settings = properties.storage().cloudinary();
        require(settings == null ? null : settings.cloudName(), "CLOUDINARY_CLOUD_NAME");
        require(settings == null ? null : settings.apiKey(), "CLOUDINARY_API_KEY");
        require(settings == null ? null : settings.apiSecret(), "CLOUDINARY_API_SECRET");
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", settings.cloudName(),
                "api_key", settings.apiKey(),
                "api_secret", settings.apiSecret(),
                "secure", true));
    }

    @Bean
    CloudinaryAssetClient cloudinaryAssetClient(Cloudinary cloudinary) {
        return new SdkCloudinaryAssetClient(cloudinary);
    }

    @Bean
    PhotoStorage cloudinaryPhotoStorage(CloudinaryAssetClient client, PhotoShareProperties properties) {
        return new CloudinaryPhotoStorage(client, properties.storage().maxFileSizeBytes());
    }

    private void require(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " must be configured when STORAGE_DRIVER=cloudinary");
        }
    }
}
