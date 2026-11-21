package com.algolearner.platform.profile;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.storage.*;
import java.io.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** External object storage/scanner fixtures; does not certify a real Supabase deployment. */
class AvatarRemoteStorageTest {
    @TempDir Path directory;
    final PrivateObjectStorage objects=mock(PrivateObjectStorage.class);
    final MalwareScanner scanner=mock(MalwareScanner.class);
    static <T> ObjectProvider<T> provider(Class<T> type,T value) {
        var beans=new StaticListableBeanFactory();if(value!=null)beans.addBean("fixture",value);return beans.getBeanProvider(type);
    }
    AvatarStorage storage(PrivateObjectStorage objectStore,MalwareScanner malware,long max) throws Exception {
        return new AvatarStorage(directory.toString(),max,16_000_000L,"image/png,image/jpeg",provider(PrivateObjectStorage.class,objectStore),provider(MalwareScanner.class,malware));
    }
    byte[] png() throws IOException {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
    }
    @Test void productionUsesPrivateRemotePngAndScansBeforeUpload() throws Exception {
        var bytes=png();var stored=new java.util.HashMap<String,byte[]>();
        doAnswer(call->{stored.put(call.getArgument(0),call.getArgument(2));return null;}).when(objects).upload(anyString(),eq("image/png"),any());
        doAnswer(call->{((OutputStream)call.getArgument(1)).write(stored.get(call.getArgument(0)));return null;}).when(objects).download(anyString(),any());
        var storage=storage(objects,scanner,5_242_880);var key=storage.store(new MockMultipartFile("file","avatar.png","image/png",bytes));
        assertThat(key).matches("[0-9a-f-]{36}/[0-9a-f-]{36}");
        assertThat(storage.read(key).getFilename()).isEqualTo("avatar.png");
        assertThat(storage.read(key).getContentAsByteArray()).isEqualTo(bytes);
        var order=inOrder(scanner,objects);order.verify(scanner,times(2)).requireClean(bytes);order.verify(objects).upload(eq(key),eq("image/png"),eq(bytes));
        try(var files=Files.list(directory)){assertThat(files).isEmpty();}
    }
    @Test void missingRemoteOrScannerFailsClosedWhileLegacyReadStillWorks() throws Exception {
        String key=java.util.UUID.randomUUID()+".png";Files.write(directory.resolve(key),png());
        for(var storage:java.util.List.of(storage(null,scanner,1000),storage(objects,null,1000))) {
            assertThatThrownBy(()->storage.store(new MockMultipartFile("file",png()))).isInstanceOfSatisfying(ApiException.class,
                    e->assertThat(e.getHttpStatus().value()).isEqualTo(503));
            assertThat(storage.read(key).getContentAsByteArray()).isEqualTo(png());
        }
        verifyNoInteractions(objects);
    }
    @Test void remoteReadIsBoundedAndDoesNotAcceptArbitraryUrls() throws Exception {
        doAnswer(call->{((OutputStream)call.getArgument(1)).write(new byte[101]);return null;}).when(objects).download(anyString(),any());
        var storage=storage(objects,scanner,100);
        assertThatThrownBy(()->storage.read(java.util.UUID.randomUUID()+"/"+java.util.UUID.randomUUID())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->storage.read("https://example.test/avatar")).isInstanceOf(ApiException.class);
        verify(objects,times(1)).download(anyString(),any());
    }
    @Test void checksumMismatchCompensatesTheRemoteObject() throws Exception {
        doAnswer(call->{((OutputStream)call.getArgument(1)).write(new byte[]{1});return null;}).when(objects).download(anyString(),any());
        var storage=storage(objects,scanner,1000);
        assertThatThrownBy(()->storage.store(new MockMultipartFile("file",png()))).isInstanceOfSatisfying(ApiException.class,
                failure->assertThat(failure.getHttpStatus().value()).isEqualTo(503));
        verify(objects).delete(anyString());
    }
    @Test void unavailableProviderKeepsTheProfilePointerAndOldLocalFile() throws Exception {
        var id=java.util.UUID.randomUUID();String old=java.util.UUID.randomUUID()+".png";Files.write(directory.resolve(old),png());
        var repository=mock(ProfileRepository.class);var access=mock(com.algolearner.platform.common.security.AccountAccess.class);
        when(access.activeRole(id)).thenReturn("STUDENT");
        when(repository.own(id)).thenReturn(new ProfileRepository.Profile(id,"Name","student","s@example.test",old,null,"STUDENT",java.time.Instant.EPOCH,false));
        var service=new ProfileService(repository,mock(PracticeStatisticsService.class),storage(null,scanner,1000),access,java.time.Clock.systemUTC());
        assertThatThrownBy(()->service.avatar(id,new MockMultipartFile("file",png()))).isInstanceOfSatisfying(ApiException.class,
                failure->assertThat(failure.getHttpStatus().value()).isEqualTo(503));
        verify(repository).own(id);verifyNoMoreInteractions(repository);assertThat(directory.resolve(old)).exists();
    }
    @Test void scannerFailureNeverUploadsAndLegacyFileRemains() throws Exception {
        doThrow(new IllegalStateException("scanner unavailable")).when(scanner).requireClean(any());
        var storage=storage(objects,scanner,1000);
        assertThatThrownBy(()->storage.store(new MockMultipartFile("file",png()))).isInstanceOf(ApiException.class);
        verifyNoInteractions(objects);
    }
    @Test void cleanupAndEnqueueFailureNeverReplaceTheOriginalUploadException() throws Exception {
        var cleanup=mock(StorageCleanupQueue.class);
        var original=new ApiException(com.algolearner.platform.common.exception.ErrorCode.FILE_STORAGE_UNAVAILABLE,"Original upload failure");
        doThrow(original).when(objects).upload(anyString(),anyString(),any());
        doThrow(new IllegalStateException("provider details must stay hidden")).when(objects).delete(anyString());
        doThrow(new IllegalStateException("database unavailable")).when(cleanup).enqueue(anyString());
        var storage=new AvatarStorage(directory.toString(),1000,16_000_000L,"image/png",
                provider(PrivateObjectStorage.class,objects),provider(MalwareScanner.class,scanner),
                provider(StorageCleanupQueue.class,cleanup));
        assertThatThrownBy(()->storage.store(new MockMultipartFile("file",png()))).isSameAs(original);
        verify(cleanup).enqueue(matches("[0-9a-f-]{36}/[0-9a-f-]{36}"));
    }
}
