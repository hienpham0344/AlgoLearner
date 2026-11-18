package com.algolearner.platform.storage;

/** Bytes have passed structural validation; malware scanning is a separate gate. */
public record ValidatedFile(String name, String mediaType, FileKind kind, byte[] bytes) {
    public ValidatedFile {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
