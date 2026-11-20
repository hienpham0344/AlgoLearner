package com.algolearner.platform.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Profile("!mock")
public class FileStorageConfiguration {
    @Bean
    FileValidator fileValidator() { return new FileValidator(); }

    @Bean
    PrivateObjectStorage privateObjectStorage(@Value("${app.files.supabase-url:}") String url,
            @Value("${app.files.service-key:}") String key,
            @Value("${app.files.bucket:learning-private}") String bucket) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new SupabaseObjectStorage(RestClient.builder().requestFactory(factory), url, key, bucket);
    }

    @Bean
    MalwareScanner malwareScanner(@Value("${app.files.scanner-host:}") String host,
            @Value("${app.files.scanner-port:3310}") int port,
            @Value("${app.files.scanner-timeout-ms:30000}") int timeoutMillis) {
        return new ClamdMalwareScanner(host, port, timeoutMillis);
    }
}
