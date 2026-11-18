package com.algolearner.platform.storage;

import java.io.IOException;
import java.io.OutputStream;

public interface PrivateObjectStorage {
    void upload(String key, String mediaType, byte[] bytes);
    void download(String key, OutputStream output) throws IOException;
    void delete(String key);
    default boolean isAvailable() { return true; }
}
