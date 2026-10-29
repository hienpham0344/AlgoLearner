package com.algolearner.platform.profile;

import com.algolearner.platform.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class AvatarStorageTest {
    @TempDir Path directory;
    AvatarStorage storage(long maxBytes,long pixels) {
        return new AvatarStorage(directory.toString(),maxBytes,pixels,"image/png,image/jpeg");
    }
    byte[] png() throws Exception {
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
        return out.toByteArray();
    }
    @Test void rejectsEmptyOversizedAndForgedImages() {
        var storage=storage(100,100);
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file",new byte[0])))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file",new byte[101])))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2})))
                .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }
    @Test void rejectsExcessiveDimensions() throws Exception {
        var file=new MockMultipartFile("file",png());
        assertThatThrownBy(() -> storage(1000,3).store(file)).isInstanceOf(ApiException.class);
    }
    @Test void reencodesImageAndPreventsTraversal() throws Exception {
        var out=new ByteArrayOutputStream();
        out.write(png()); out.write("<script>payload</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var storage=storage(1000,100);
        String key=storage.store(new MockMultipartFile("file","../../evil.png","image/png",out.toByteArray()));
        assertThat(key).matches("[0-9a-f-]{36}\\.png");
        assertThat(storage.read(key).getContentAsByteArray()).isEqualTo(png());
        assertThatThrownBy(() -> storage.read("../../outside.png")).isInstanceOf(ApiException.class);
        storage.delete(key);
        assertThat(directory.resolve(key)).doesNotExist();
    }
    @Test void unavailableStorageReturns503() throws Exception {
        Path blocker=Files.writeString(directory.resolve("not-a-directory"),"blocked");
        var storage=new AvatarStorage(blocker.toString(),1000,100,"image/png");
        var file=new MockMultipartFile("file",png());
        assertThatThrownBy(() -> storage.store(file)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
