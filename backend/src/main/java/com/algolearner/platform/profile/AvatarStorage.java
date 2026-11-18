package com.algolearner.platform.profile;

import com.algolearner.platform.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import com.algolearner.platform.storage.PrivateObjectStorage;
import com.algolearner.platform.storage.MalwareScanner;
import com.algolearner.platform.storage.StorageCleanupQueue;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import com.algolearner.platform.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AvatarStorage {
    private static final Logger LOG=LoggerFactory.getLogger(AvatarStorage.class);
    private final Path directory;
    private final long maxBytes;
    private final long maxPixels;
    private final Set<String> allowedTypes;
    private ObjectProvider<PrivateObjectStorage> objects;
    private ObjectProvider<MalwareScanner> scanners;
    private ObjectProvider<StorageCleanupQueue> cleanup;
    private boolean localFixture;
    /** Explicit local fixture constructor; Spring always uses the private storage constructor below. */
    public AvatarStorage(@Value("${app.storage.avatar-directory}") String directory,
            @Value("${app.storage.max-bytes}") long maxBytes,@Value("${app.storage.max-pixels}") long maxPixels,
            @Value("${app.storage.allowed-types}") String allowedTypes) {
        this.localFixture=true;
        this.directory=Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes=maxBytes; this.maxPixels=maxPixels;
        this.allowedTypes=new HashSet<>(Arrays.asList(allowedTypes.split(",")));
        if(maxBytes<1 || maxBytes>=Integer.MAX_VALUE || maxPixels<1) throw new IllegalArgumentException("Invalid avatar size configuration");
    }
    /** Explicit remote fixture constructor; production also supplies durable cleanup. */
    public AvatarStorage(@Value("${app.storage.avatar-directory}") String directory,
            @Value("${app.storage.max-bytes}") long maxBytes,@Value("${app.storage.max-pixels}") long maxPixels,
            @Value("${app.storage.allowed-types}") String allowedTypes,ObjectProvider<PrivateObjectStorage> objects,
            ObjectProvider<MalwareScanner> scanners) {
        this(directory,maxBytes,maxPixels,allowedTypes);
        this.localFixture=false;this.objects=objects;this.scanners=scanners;
    }
    @Autowired
    public AvatarStorage(@Value("${app.storage.avatar-directory}") String directory,
            @Value("${app.storage.max-bytes}") long maxBytes,@Value("${app.storage.max-pixels}") long maxPixels,
            @Value("${app.storage.allowed-types}") String allowedTypes,ObjectProvider<PrivateObjectStorage> objects,
            ObjectProvider<MalwareScanner> scanners,ObjectProvider<StorageCleanupQueue> cleanup) {
        this(directory,maxBytes,maxPixels,allowedTypes,objects,scanners);
        this.cleanup=cleanup;
    }
    public String store(MultipartFile file) {
        if(file.isEmpty()) throw failure(ErrorCode.VALIDATION_FAILED,"Avatar file is empty");
        if(file.getSize()>maxBytes) throw failure(ErrorCode.AVATAR_TOO_LARGE,"Avatar exceeds upload limit");
        Path temporary=null;
        try (InputStream input=file.getInputStream()) {
            byte[] bytes=input.readNBytes((int)maxBytes+1);
            if(bytes.length>maxBytes) throw failure(ErrorCode.AVATAR_TOO_LARGE,"Avatar exceeds upload limit");
            if(!localFixture) {
                requireRemote();
                scan(bytes);
            }
            BufferedImage image;
            String extension;
            try(ImageInputStream imageInput=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(imageInput);
                if(!readers.hasNext()) throw invalidType();
                var reader=readers.next();
                try {
                    reader.setInput(imageInput,true,true);
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    extension=switch(format) { case "jpeg","jpg" -> "jpg"; case "png" -> "png"; default -> throw invalidType(); };
                    String mime=extension.equals("jpg")?"image/jpeg":"image/png";
                    if(!allowedTypes.contains(mime)) throw invalidType();
                    if((long)reader.getWidth(0)*reader.getHeight(0)>maxPixels)
                        throw failure(ErrorCode.VALIDATION_FAILED,"Avatar dimensions exceed configured limit");
                    image=reader.read(0);
                } finally { reader.dispose(); }
            }
            if(!localFixture) {
                var normalized=new LimitedOutput();
                if(!ImageIO.write(image,"png",normalized)) throw invalidType();
                byte[] clean=normalized.toByteArray();scan(clean);
                return uploadVerified(clean);
            }
            Files.createDirectories(directory);
            temporary=Files.createTempFile(directory,"upload-",".tmp");
            // Re-encode decoded pixels to strip metadata and trailing/polyglot content.
            if(!ImageIO.write(image,extension,temporary.toFile())) throw invalidType();
            if(Files.size(temporary)>maxBytes) throw failure(ErrorCode.AVATAR_TOO_LARGE,"Normalized avatar exceeds upload limit");
            String key=UUID.randomUUID()+"."+extension;
            Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch(ApiException ex) { throw ex; }
        catch(javax.imageio.IIOException ex) { throw invalidType(); }
        catch(IOException ex) { throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Avatar could not be stored"); }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ex) { LOG.warn("Unable to clean temporary avatar"); } }
    }
    public Resource read(String key) {
        if(remoteKey(key)) return new ByteArrayResource(download(key)) {
            @Override public String getFilename(){return "avatar.png";}
        };
        Path file=path(key);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw failure(ErrorCode.PROFILE_NOT_FOUND,"Avatar not found");
        return new FileSystemResource(file);
    }
    public void delete(String key) {
        if(key==null) return;
        if(remoteKey(key)) {
            try {
                var queue=cleanup==null?null:cleanup.getIfAvailable();
                if(queue!=null&&queue.isCurrentAvatar(key))return;
                requireRemote().delete(key);
            } catch(RuntimeException ex) {
                // Compensation must never replace the original upload/profile failure.
                try {
                    var queue=cleanup==null?null:cleanup.getIfAvailable();
                    if(queue==null)LOG.error("PRIVATE_AVATAR_CLEANUP_QUEUE_UNAVAILABLE");
                    else queue.enqueue(key);
                } catch(RuntimeException queueFailure) { LOG.error("PRIVATE_AVATAR_CLEANUP_ENQUEUE_FAILED"); }
                LOG.warn("PRIVATE_AVATAR_DELETE_DEFERRED");
            }
            return;
        }
        try { Files.deleteIfExists(path(key)); } catch(IOException|RuntimeException ex) { LOG.warn("Unable to remove unused avatar"); }
    }
    public boolean legacyKey(String key) {return key!=null&&key.matches("[0-9a-f-]{36}\\.(png|jpg)");}
    private boolean remoteKey(String key) {
        if(key==null||!key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}")) return false;
        try {var parts=key.split("/");return UUID.fromString(parts[0]).toString().equals(parts[0])&&UUID.fromString(parts[1]).toString().equals(parts[1]);}
        catch(IllegalArgumentException invalid){return false;}
    }
    /** Migration accepts only existing local keys, never a URL or caller-selected remote destination. */
    public String migrateLegacy(String key) {
        if(localFixture)throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Migration requires private remote storage");
        if(!legacyKey(key)) throw failure(ErrorCode.PROFILE_NOT_FOUND,"Legacy avatar not found");
        try {
            byte[] bytes;
            try(var input=read(key).getInputStream()) {bytes=input.readNBytes((int)maxBytes+1);}
            if(bytes.length>maxBytes)throw failure(ErrorCode.AVATAR_TOO_LARGE,"Avatar exceeds upload limit");
            final byte[] content=bytes;
            return store(new org.springframework.web.multipart.MultipartFile() {
                public String getName(){return "avatar";}public String getOriginalFilename(){return key;}
                public String getContentType(){return "application/octet-stream";}public boolean isEmpty(){return content.length==0;}
                public long getSize(){return content.length;}public byte[] getBytes(){return content.clone();}
                public InputStream getInputStream(){return new ByteArrayInputStream(content);}
                public void transferTo(File destination)throws IOException{Files.write(destination.toPath(),content);}
            });
        } catch(IOException failure){throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Legacy avatar could not be read");}
    }
    private PrivateObjectStorage requireRemote() {
        var storage=objects==null?null:objects.getIfAvailable();
        if(storage==null||scanners==null||scanners.getIfAvailable()==null)throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Private avatar storage unavailable");
        return storage;
    }
    private void scan(byte[] bytes) {
        try{scanners.getObject().requireClean(bytes);}
        catch(ApiException failure){throw failure;}
        catch(RuntimeException failure){throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Avatar scanner unavailable");}
    }
    private String uploadVerified(byte[] bytes) {
        String key=UUID.randomUUID()+"/"+UUID.randomUUID();
        try {
            requireRemote().upload(key,"image/png",bytes);
            if(!java.security.MessageDigest.isEqual(checksum(bytes),checksum(download(key))))
                throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Private avatar verification failed");
            return key;
        } catch(RuntimeException failure) {
            delete(key);
            if(failure instanceof ApiException api)throw api;
            throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Private avatar storage unavailable");
        }
    }
    private byte[] checksum(byte[] bytes) {
        try{return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}
    }
    private byte[] download(String key) {
        try {var output=new LimitedOutput();requireRemote().download(key,output);return output.toByteArray();}
        catch(ApiException failure){throw failure;}
        catch(IOException|RuntimeException failure){throw failure(ErrorCode.FILE_STORAGE_UNAVAILABLE,"Private avatar could not be read");}
    }
    private final class LimitedOutput extends ByteArrayOutputStream {
        @Override public synchronized void write(byte[] bytes,int offset,int length) {
            if(length>maxBytes-count)throw failure(ErrorCode.AVATAR_TOO_LARGE,"Avatar exceeds download limit");
            super.write(bytes,offset,length);
        }
        @Override public synchronized void write(int value) {
            if(count>=maxBytes)throw failure(ErrorCode.AVATAR_TOO_LARGE,"Avatar exceeds download limit");super.write(value);
        }
    }
    private Path path(String key) {
        if(key==null || !key.matches("[0-9a-f-]{36}\\.(png|jpg)")) throw failure(ErrorCode.PROFILE_NOT_FOUND,"Avatar not found");
        Path result=directory.resolve(key).normalize();
        if(!result.startsWith(directory)) throw invalidType();
        return result;
    }
    private ApiException invalidType() { return failure(ErrorCode.UNSUPPORTED_AVATAR_TYPE,"Avatar must be a supported image"); }
    private ApiException failure(ErrorCode error,String message) { return new ApiException(error,message); }
}
