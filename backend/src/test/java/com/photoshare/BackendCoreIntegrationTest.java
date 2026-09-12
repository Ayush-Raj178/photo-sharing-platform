package com.photoshare;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BackendCoreIntegrationTest {
    private static final byte[] PNG = createPng();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void photoTableContainsMetadataButNoBinaryColumn() {
        List<String> dataTypes = jdbc.queryForList("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'photos'
                """, String.class);

        assertThat(dataTypes).isNotEmpty();
        assertThat(dataTypes).noneMatch(type -> {
            String normalized = type.toUpperCase();
            return normalized.contains("BINARY") || normalized.contains("BLOB");
        });
    }

    @Test
    void eightCharacterPasswordsAreAcceptedAndSevenCharacterPasswordsAreRejected() throws Exception {
        String suffix = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short-" + suffix + "@example.test\","
                                + "\"displayName\":\"Short Password\",\"password\":\"Seven77\"}"))
                .andExpect(status().isBadRequest());

        MvcResult registration = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eight-" + suffix + "@example.test\","
                                + "\"displayName\":\"Eight Password\",\"password\":\"Eight888\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String adminToken = read(registration, "$.accessToken");
        String eventId = createEvent(adminToken, "Password Boundary Event");

        mvc.perform(post("/api/v1/events/{eventId}/members", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member-short-" + suffix + "@example.test\","
                                + "\"displayName\":\"Short Member\",\"password\":\"Seven77\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/events/{eventId}/members", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"member-eight-" + suffix + "@example.test\","
                                + "\"displayName\":\"Eight Member\",\"password\":\"Eight888\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void authenticationMembershipAndEventAuthorizationAreEnforced() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Session adminA = register("admin-a-" + suffix + "@example.test", "Admin A");
        Session adminB = register("admin-b-" + suffix + "@example.test", "Admin B");

        String eventA = createEvent(adminA.token(), "Event A");
        String eventB = createEvent(adminB.token(), "Event B");
        Member memberA = addNewMember(adminA.token(), eventA,
                "member-a-" + suffix + "@example.test", "Member A");
        String memberToken = login(memberA.email(), "StrongPassword123!");

        mvc.perform(get("/api/v1/events").header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(eventA));

        mvc.perform(get("/api/v1/events/{id}", eventA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/events/{id}", eventB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/events/{id}", eventA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB.token())))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/events/{eventId}/galleries", eventA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/events/{eventId}/members", eventA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/events/{id}", eventA))
                .andExpect(status().isUnauthorized());

        assertThat(adminA.id()).isNotEqualTo(adminB.id());
    }

    @Test
    void uploadsGalleryPublicationPinAndPublicIsolationWorkEndToEnd() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Session admin = register("owner-" + suffix + "@example.test", "Gallery Owner");
        String eventOne = createEvent(admin.token(), "Event One");
        Member memberOne = addNewMember(admin.token(), eventOne,
                "uploader-one-" + suffix + "@example.test", "Uploader One");
        String memberOneToken = login(memberOne.email(), "StrongPassword123!");

        List<String> uploadedOne = uploadTwo(memberOneToken, eventOne, "one-a.png", "one-b.png");
        assertThat(uploadedOne).hasSize(2);

        Member memberTwo = addNewMember(admin.token(), eventOne,
                "uploader-two-" + suffix + "@example.test", "Uploader Two");
        String memberTwoToken = login(memberTwo.email(), "StrongPassword123!");
        String otherMemberPhoto = uploadOne(memberTwoToken, eventOne, "other.png");

        mvc.perform(get("/api/v1/events/{eventId}/photos", eventOne)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberOneToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mvc.perform(get("/api/v1/events/{eventId}/photos", eventOne)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        mvc.perform(get("/api/v1/events/{eventId}/photos/{photoId}", eventOne, otherMemberPhoto)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberOneToken)))
                .andExpect(status().isNotFound());

        String galleryOne = createGallery(admin.token(), eventOne, "Published Gallery");
        replaceSelection(admin.token(), eventOne, galleryOne, List.of(uploadedOne.get(0)));
        setPin(admin.token(), eventOne, galleryOne, "123456");

        String shareTokenOne = shareToken(publish(admin.token(), eventOne, galleryOne, "{}"));

        mvc.perform(post("/api/v1/public/galleries/{token}/access", "not-" + shareTokenOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"123456\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/public/galleries/{token}/access", shareTokenOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_GALLERY_ACCESS"));

        String grantOne = galleryAccess(shareTokenOne, "123456");

        mvc.perform(get("/api/v1/public/galleries/{token}", shareTokenOne)
                        .header(HttpHeaders.AUTHORIZATION, bearer(grantOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(uploadedOne.get(0)));

        mvc.perform(get("/api/v1/public/galleries/{token}/photos/{photoId}/content",
                        shareTokenOne, uploadedOne.get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(grantOne)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mvc.perform(get("/api/v1/public/galleries/{token}/photos/{photoId}/content",
                        shareTokenOne, uploadedOne.get(1))
                        .header(HttpHeaders.AUTHORIZATION, bearer(grantOne)))
                .andExpect(status().isNotFound());

        String eventTwo = createEvent(admin.token(), "Event Two");
        addExistingMember(admin.token(), eventTwo, memberOne.id());
        String eventTwoPhoto = uploadOne(memberOneToken, eventTwo, "two.png");
        String galleryTwo = createGallery(admin.token(), eventTwo, "Second Gallery");

        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/photos", eventTwo, galleryTwo)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoIds\":[\"" + uploadedOne.get(0) + "\"]}"))
                .andExpect(status().isNotFound());

        replaceSelection(admin.token(), eventTwo, galleryTwo, List.of(eventTwoPhoto));
        setPin(admin.token(), eventTwo, galleryTwo, "222222");
        String shareTokenTwo = shareToken(publish(admin.token(), eventTwo, galleryTwo, "{}"));

        mvc.perform(get("/api/v1/public/galleries/{token}", shareTokenTwo)
                        .header(HttpHeaders.AUTHORIZATION, bearer(grantOne)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/events/{eventId}/galleries/{galleryId}/publish", eventOne, galleryOne)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberOneToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        String republished = publish(admin.token(), eventOne, galleryOne,
                "{\"photoIds\":[\"" + uploadedOne.get(1) + "\"],\"pin\":\"654321\"}");
        assertThat(shareToken(republished)).isEqualTo(shareTokenOne);

        mvc.perform(get("/api/v1/public/galleries/{token}", shareTokenOne)
                        .header(HttpHeaders.AUTHORIZATION, bearer(grantOne)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/public/galleries/{token}/access", shareTokenOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"123456\"}"))
                .andExpect(status().isUnauthorized());

        String newGrant = galleryAccess(shareTokenOne, "654321");
        mvc.perform(get("/api/v1/public/galleries/{token}", shareTokenOne)
                        .header(HttpHeaders.AUTHORIZATION, bearer(newGrant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(uploadedOne.get(1)));
    }

    @Test
    void unpublishedGalleryAndInvalidUploadRemainPrivate() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Session admin = register("draft-" + suffix + "@example.test", "Draft Owner");
        String event = createEvent(admin.token(), "Draft Event");
        Member member = addNewMember(admin.token(), event,
                "draft-member-" + suffix + "@example.test", "Draft Member");
        String memberToken = login(member.email(), "StrongPassword123!");
        String gallery = createGallery(admin.token(), event, "Draft");
        String internalToken = galleryShareTokenFromDatabaseContractIsNotExposed(admin.token(), event, gallery);
        assertThat(internalToken).isNull();

        mvc.perform(post("/api/v1/public/galleries/{token}/access", "unknown-draft-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"123456\"}"))
                .andExpect(status().isNotFound());

        MockMultipartFile invalid = new MockMultipartFile(
                "files", "payload.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/events/{eventId}/photos", event)
                        .file(invalid)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].error.code").value("INVALID_IMAGE"));

        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void photoPaginationSearchAndFiltersStayDatabasePagedAndEventScoped() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Session owner = register("page-owner-" + suffix + "@example.test", "Page Owner");
        String event = createEvent(owner.token(), "Paged Event");
        Member first = addNewMember(owner.token(), event,
                "page-first-" + suffix + "@example.test", "First Uploader");
        Member second = addNewMember(owner.token(), event,
                "page-second-" + suffix + "@example.test", "Second Uploader");
        String firstToken = login(first.email(), "StrongPassword123!");
        String secondToken = login(second.email(), "StrongPassword123!");
        String portraitOne = uploadOne(firstToken, event, "Portrait Alpha.png");
        String portraitTwo = uploadOne(firstToken, event, "portrait-beta.png");
        String ceremony = uploadOne(secondToken, event, "ceremony.png");

        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("search", "PORTRAIT").param("page", "0").param("pageSize", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("search", "portrait").param("page", "1").param("pageSize", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("uploaderId", second.id()).param("pageSize", "24")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ceremony));

        String gallery = createGallery(owner.token(), event, "Filtered Gallery");
        replaceSelection(owner.token(), event, gallery, List.of(portraitOne));
        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("selected", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(portraitOne));
        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("selected", "false").param("search", "portrait")
                        .param("page", "0").param("pageSize", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(portraitTwo));

        replaceSelection(owner.token(), event, gallery, List.of(portraitOne, portraitTwo, ceremony));
        setPin(owner.token(), event, gallery, "123456");
        String shareToken = shareToken(publish(owner.token(), event, gallery, "{}"));
        String grant = galleryAccess(shareToken, "123456");
        mvc.perform(get("/api/v1/public/galleries/{token}", shareToken)
                        .param("page", "0").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(grant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/v1/public/galleries/{token}", shareToken)
                        .param("page", "1").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(grant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        Session otherOwner = register("page-other-" + suffix + "@example.test", "Other Owner");
        String otherEvent = createEvent(otherOwner.token(), "Other Event");
        Member otherMember = addNewMember(otherOwner.token(), otherEvent,
                "page-other-member-" + suffix + "@example.test", "Other Uploader");
        uploadOne(login(otherMember.email(), "StrongPassword123!"), otherEvent, "portrait-secret.png");
        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("search", "secret")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));

        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("pageSize", "101")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("page", "0").param("pageSize", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/v1/events/{eventId}/photos", event)
                        .param("uploaderId", second.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void optionalGalleryExpiryBlocksPinAndExistingGrantsButNotAdminManagement() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Session admin = register("expiry-owner-" + suffix + "@example.test", "Expiry Owner");
        String event = createEvent(admin.token(), "Expiry Event");
        Member member = addNewMember(admin.token(), event,
                "expiry-member-" + suffix + "@example.test", "Expiry Member");
        String memberToken = login(member.email(), "StrongPassword123!");
        String photo = uploadOne(memberToken, event, "future.png");
        String gallery = createGallery(admin.token(), event, "Expiring Gallery");
        replaceSelection(admin.token(), event, gallery, List.of(photo));
        setPin(admin.token(), event, gallery, "123456");
        String shareToken = shareToken(publish(admin.token(), event, gallery, "{}"));

        String nonExpiringGrant = galleryAccess(shareToken, "123456");
        mvc.perform(get("/api/v1/public/galleries/{token}", shareToken)
                        .header(HttpHeaders.AUTHORIZATION, bearer(nonExpiringGrant)))
                .andExpect(status().isOk());

        String future = Instant.now().plusSeconds(3600).toString();
        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/expiry", event, gallery)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + future + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").isNotEmpty());
        String futureGrant = galleryAccess(shareToken, "123456");

        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/expiry", event, gallery)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":null}"))
                .andExpect(status().isForbidden());

        String past = Instant.now().minusSeconds(60).toString();
        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/expiry", event, gallery)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\":\"" + past + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/public/galleries/{token}/access", shareToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"123456\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("GALLERY_EXPIRED"))
                .andExpect(jsonPath("$.error.message").value("Gallery has expired."));
        mvc.perform(get("/api/v1/public/galleries/{token}", shareToken)
                        .header(HttpHeaders.AUTHORIZATION, bearer(futureGrant)))
                .andExpect(status().isGone());
        mvc.perform(get("/api/v1/public/galleries/{token}/photos/{photoId}/content", shareToken, photo)
                        .header(HttpHeaders.AUTHORIZATION, bearer(futureGrant)))
                .andExpect(status().isGone());
        mvc.perform(get("/api/v1/events/{eventId}/galleries/{galleryId}", event, gallery)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/expiry", event, gallery)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").isEmpty());
        galleryAccess(shareToken, "123456");
    }

    private Session register(String email, String displayName) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"" + displayName
                                + "\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return new Session(read(result, "$.accessToken"), read(result, "$.user.id"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result, "$.accessToken");
    }

    private String createEvent(String token, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return read(result, "$.id");
    }

    private Member addNewMember(String adminToken, String eventId, String email, String displayName)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events/{eventId}/members", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"" + displayName
                                + "\",\"password\":\"StrongPassword123!\"}"))
                .andExpect(status().isCreated()).andReturn();
        return new Member(read(result, "$.user.id"), email);
    }

    private void addExistingMember(String adminToken, String eventId, String memberId) throws Exception {
        mvc.perform(post("/api/v1/events/{eventId}/members", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + memberId + "\"}"))
                .andExpect(status().isCreated());
    }

    private List<String> uploadTwo(String token, String eventId, String first, String second) throws Exception {
        MockMultipartFile one = new MockMultipartFile("files", first, "image/png", PNG);
        MockMultipartFile two = new MockMultipartFile("files", second, "image/png", PNG);
        MvcResult result = mvc.perform(multipart("/api/v1/events/{eventId}/photos", eventId)
                        .file(one).file(two).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("UPLOADED"))
                .andExpect(jsonPath("$.results[1].status").value("UPLOADED"))
                .andReturn();
        return List.of(read(result, "$.results[0].photo.id"), read(result, "$.results[1].photo.id"));
    }

    private String uploadOne(String token, String eventId, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", filename, "image/png", PNG);
        MvcResult result = mvc.perform(multipart("/api/v1/events/{eventId}/photos", eventId)
                        .file(file).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("UPLOADED"))
                .andReturn();
        return read(result, "$.results[0].photo.id");
    }

    private String createGallery(String token, String eventId, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events/{eventId}/galleries", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return read(result, "$.id");
    }

    private void replaceSelection(String token, String eventId, String galleryId, List<String> ids)
            throws Exception {
        String body = "{\"photoIds\":[" + ids.stream().map(id -> "\"" + id + "\"")
                .reduce((left, right) -> left + "," + right).orElse("") + "]}";
        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/photos", eventId, galleryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void setPin(String token, String eventId, String galleryId, String pin) throws Exception {
        mvc.perform(put("/api/v1/events/{eventId}/galleries/{galleryId}/pin", eventId, galleryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String publish(String token, String eventId, String galleryId, String body) throws Exception {
        return mvc.perform(post("/api/v1/events/{eventId}/galleries/{galleryId}/publish", eventId, galleryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String galleryAccess(String shareToken, String pin) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/public/galleries/{token}/access", shareToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return read(result, "$.accessToken");
    }

    private String galleryShareTokenFromDatabaseContractIsNotExposed(String token, String event, String gallery)
            throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/events/{eventId}/galleries/{galleryId}", event, gallery)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareUrl").isEmpty())
                .andReturn();
        return readNullable(result, "$.shareUrl");
    }

    private String shareToken(String responseBody) {
        String url = JsonPath.read(responseBody, "$.shareUrl");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String readNullable(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static byte[] createPng() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0x336699);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create test image", exception);
        }
    }

    private record Session(String token, String id) {}
    private record Member(String id, String email) {}
}
