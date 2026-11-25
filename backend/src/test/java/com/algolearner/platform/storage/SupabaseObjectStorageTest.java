package com.algolearner.platform.storage;

import com.algolearner.platform.common.exception.AppException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupabaseObjectStorageTest {
    private static final String BASE = "https://storage-provider.example.test/storage/v1";
    private static final String KEY = "00000000-0000-0000-0000-000000000001/00000000-0000-0000-0000-000000000002";
    private MockRestServiceServer server;
    private SupabaseObjectStorage storage;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabaseObjectStorage(builder, "https://storage-provider.example.test", "test-service-key", "learning-private");
    }

    @Test
    void uploadsToVerifiedPrivateBucketWithoutUpsert() {
        expectPrivateBucket();
        server.expect(requestTo(BASE + "/object/learning-private/" + KEY)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-service-key"))
                .andExpect(header("x-upsert", "false"))
                .andExpect(content().bytes("payload".getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        storage.upload(KEY, "application/pdf", "payload".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void refusesPublicBucketBeforeUploadingBytes() {
        server.expect(requestTo(BASE + "/bucket/learning-private"))
                .andRespond(withSuccess("{\"public\":true}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> storage.upload(KEY, "application/pdf", new byte[] {1}))
                .isInstanceOf(AppException.class).hasMessageContaining("private");
        server.verify();
    }

    @Test
    void streamsAuthenticatedDownloadWithoutPublicUrl() throws Exception {
        expectPrivateBucket();
        server.expect(requestTo(BASE + "/object/authenticated/learning-private/" + KEY))
                .andExpect(header("Authorization", "Bearer test-service-key"))
                .andRespond(withSuccess("stored bytes", MediaType.APPLICATION_OCTET_STREAM));
        var output = new ByteArrayOutputStream();
        storage.download(KEY, output);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("stored bytes");
        server.verify();
    }

    @Test
    void rejectsUnconfiguredProviderAndPathTraversal() {
        assertThatThrownBy(() -> new SupabaseObjectStorage(RestClient.builder(), "", "", "learning-private")
                .upload(KEY, "application/pdf", new byte[] {1})).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> storage.upload("../outside", "application/pdf", new byte[] {1}))
                .isInstanceOf(AppException.class);
    }

    private void expectPrivateBucket() {
        server.expect(requestTo(BASE + "/bucket/learning-private"))
                .andRespond(withSuccess("{\"public\":false}", MediaType.APPLICATION_JSON));
    }
}
