package com.photoshare;

import com.jayway.jsonpath.JsonPath;
import com.photoshare.photo.storage.PhotoStorage;
import com.photoshare.photo.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PhotoStorageFailureIntegrationTest {
    private static final byte[] PNG = createPng();

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PhotoStorage storage;

    @Test
    void contentAuthorizesBeforeReadingStreamsOnlyBytesAndMapsMissingStorageToSafe503() throws Exception {
        when(storage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> new PhotoStorage.StoredPhoto(invocation.getArgument(0),
                        invocation.getArgument(2), invocation.getArgument(3), PNG.length));
        String suffix = UUID.randomUUID().toString();
        String owner = register("read-owner-" + suffix + "@example.test");
        String outsider = register("read-outsider-" + suffix + "@example.test");
        String event = createEvent(owner);
        String otherEvent = createEvent(owner);
        String uploaderEmail = "read-uploader-" + suffix + "@example.test";
        String colleagueEmail = "read-colleague-" + suffix + "@example.test";
        addMember(owner, event, uploaderEmail);
        addMember(owner, event, colleagueEmail);
        String uploader = login(uploaderEmail);
        String colleague = login(colleagueEmail);
        MvcResult uploaded = mvc.perform(multipart("/api/v1/events/{eventId}/photos", event)
                        .file(new MockMultipartFile("files", "photo.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, bearer(uploader)))
                .andExpect(status().isOk()).andReturn();
        String photoId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.results[0].photo.id");
        String path = "/api/v1/events/" + event + "/photos/" + photoId + "/content";
        clearInvocations(storage);
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(colleague)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events/" + otherEvent + "/photos/" + photoId + "/content")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isNotFound());
        verify(storage, never()).read(anyString());

        when(storage.read(anyString())).thenReturn(new ByteArrayResource(PNG));
        for (String allowed : new String[]{owner, uploader}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(allowed)))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                    .andExpect(content().bytes(PNG)).andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        MvcResult metadata = mvc.perform(get("/api/v1/events/" + event + "/photos/" + photoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contentPath").value(path)).andReturn();
        assertThat(metadata.getResponse().getContentAsString()).doesNotContain("cloudinary", "storageKey", "signature", "api_key");

        when(storage.read(anyString())).thenThrow(new StorageException("Stored photo is unavailable",
                new IllegalStateException("Cloudinary 404 https://host/asset?signature=NEVER_EXPOSE")));
        MvcResult failed = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("Stored photo is unavailable")).andReturn();
        assertThat(failed.getResponse().getContentAsString()).doesNotContain("Cloudinary", "https://", "NEVER_EXPOSE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void oneCloudStorageFailureDoesNotUndoAnotherFileInTheBatch(boolean cleanupAllowed) throws Exception {
        AtomicInteger uploads = new AtomicInteger();
        when(storage.put(anyString(), any(byte[].class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    byte[] bytes = invocation.getArgument(1);
                    String contentType = invocation.getArgument(2);
                    String filename = invocation.getArgument(3);
                    if (uploads.incrementAndGet() == 2) {
                        throw new StorageException("simulated provider failure", null, cleanupAllowed);
                    }
                    return new PhotoStorage.StoredPhoto(key, contentType, filename, bytes.length);
                });

        String suffix = UUID.randomUUID().toString();
        String adminToken = register("storage-owner-" + suffix + "@example.test");
        String eventId = createEvent(adminToken);
        String memberEmail = "storage-member-" + suffix + "@example.test";
        addMember(adminToken, eventId, memberEmail);
        String memberToken = login(memberEmail);

        MockMultipartFile first = new MockMultipartFile("files", "stored.png", "image/png", PNG);
        MockMultipartFile second = new MockMultipartFile("files", "failed.png", "image/png", PNG);
        mvc.perform(multipart("/api/v1/events/{eventId}/photos", eventId)
                        .file(first).file(second)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("UPLOADED"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[1].error.code").value("STORAGE_WRITE_FAILED"));

        mvc.perform(get("/api/v1/events/{eventId}/photos", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].filename").value("stored.png"));

        if (cleanupAllowed) verify(storage).delete(anyString());
        else verify(storage, never()).delete(anyString());
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"displayName\":\"Storage Owner\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createEvent(String adminToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Storage Failure Event\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void addMember(String adminToken, String eventId, String email) throws Exception {
        mvc.perform(post("/api/v1/events/{eventId}/members", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"displayName\":\"Storage Member\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static byte[] createPng() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create test image", exception);
        }
    }
}
