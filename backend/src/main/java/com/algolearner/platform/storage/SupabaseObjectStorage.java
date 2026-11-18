package com.algolearner.platform.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class SupabaseObjectStorage implements PrivateObjectStorage {
    private final RestClient client;
    private final String bucket;
    private final boolean configured;

    public SupabaseObjectStorage(RestClient.Builder builder, String url, String serviceKey, String bucket) {
        this.bucket = bucket;
        this.configured = !url.isBlank() && !serviceKey.isBlank();
        if (configured) {
            URI endpoint = URI.create(url);
            if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || !bucket.matches("[a-z0-9][a-z0-9_-]{0,62}")) {
                throw new IllegalArgumentException("Invalid private storage configuration");
            }
            builder.baseUrl(url.replaceAll("/+$", "") + "/storage/v1")
                    .defaultHeader("Authorization", "Bearer " + serviceKey)
                    .defaultHeader("apikey", serviceKey);
        }
        this.client = builder.build();
    }

    @Override
    public void upload(String key, String mediaType, byte[] bytes) {
        validateKey(key);
        requirePrivateBucket();
        try {
            client.post().uri("/object/{bucket}/{owner}/{id}", bucket, key.split("/")[0], key.split("/")[1])
                    .contentType(MediaType.parseMediaType(mediaType)).header("x-upsert", "false")
                    .body(bytes).retrieve().toBodilessEntity();
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    @Override
    public void download(String key, OutputStream output) throws IOException {
        validateKey(key);
        requirePrivateBucket();
        try {
            client.get().uri("/object/authenticated/{bucket}/{owner}/{id}", bucket, key.split("/")[0], key.split("/")[1]).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) throw unavailable();
                response.getBody().transferTo(output);
                return null;
            });
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        requirePrivateBucket();
        try {
            client.delete().uri("/object/{bucket}/{owner}/{id}", bucket, key.split("/")[0], key.split("/")[1]).exchange((request, response) -> {
                if (!response.getStatusCode().is2xxSuccessful() && response.getStatusCode().value() != 404) throw unavailable();
                return null;
            });
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private void requirePrivateBucket() {
        if (!configured) throw unavailable();
        try {
            var metadata = client.get().uri("/bucket/{bucket}", bucket).retrieve().body(BucketMetadata.class);
            if (metadata == null || !Boolean.FALSE.equals(metadata.publicBucket())) {
                throw new StorageException("PRIVATE_STORAGE_REQUIRED", HttpStatus.SERVICE_UNAVAILABLE,
                        "File bucket must be private");
            }
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}/[a-f0-9-]{36}")) {
            throw new StorageException("INVALID_FILE_KEY", HttpStatus.BAD_REQUEST, "Invalid storage object key");
        }
    }

    private StorageException unavailable() {
        // Never propagate provider responses or authorization headers into logs or API errors.
        return new StorageException("FILE_STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Private file storage is unavailable");
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record BucketMetadata(@com.fasterxml.jackson.annotation.JsonProperty("public") Boolean publicBucket) {
    }
}
