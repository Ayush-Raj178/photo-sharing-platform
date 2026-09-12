package com.photoshare;

import com.photoshare.config.PhotoShareProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PhotoShareProperties.class)
public class PhotoShareApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoShareApplication.class, args);
    }
}

