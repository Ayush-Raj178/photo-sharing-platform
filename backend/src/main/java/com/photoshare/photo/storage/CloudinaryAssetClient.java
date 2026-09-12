package com.photoshare.photo.storage;

interface CloudinaryAssetClient {
    UploadResult upload(byte[] bytes, String publicId, String format, String originalFilename) throws Exception;
    byte[] read(String publicId, String format) throws Exception;
    DeleteResult delete(String publicId) throws Exception;

    record UploadResult(String publicId, String format, long bytes, String resourceType, String deliveryType) {}
    record DeleteResult(String result) {}
}
