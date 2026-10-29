package com.algolearner.platform.profile;

import com.algolearner.platform.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
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
    public AvatarStorage(@Value("${app.storage.avatar-directory}") String directory,
            @Value("${app.storage.max-bytes}") long maxBytes,@Value("${app.storage.max-pixels}") long maxPixels,
            @Value("${app.storage.allowed-types}") String allowedTypes) {
        this.directory=Path.of(directory).toAbsolutePath().normalize();
        this.maxBytes=maxBytes; this.maxPixels=maxPixels;
        this.allowedTypes=new HashSet<>(Arrays.asList(allowedTypes.split(",")));
        if(maxBytes<1 || maxBytes>=Integer.MAX_VALUE || maxPixels<1) throw new IllegalArgumentException("Invalid avatar size configuration");
    }
    public String store(MultipartFile file) {
        if(file.isEmpty()) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Avatar file is empty");
        if(file.getSize()>maxBytes) throw failure(HttpStatus.PAYLOAD_TOO_LARGE,"AVATAR_TOO_LARGE","Avatar exceeds upload limit");
        Path temporary=null;
        try (InputStream input=file.getInputStream()) {
            byte[] bytes=input.readNBytes((int)maxBytes+1);
            if(bytes.length>maxBytes) throw failure(HttpStatus.PAYLOAD_TOO_LARGE,"AVATAR_TOO_LARGE","Avatar exceeds upload limit");
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
                        throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Avatar dimensions exceed configured limit");
                    image=reader.read(0);
                } finally { reader.dispose(); }
            }
            Files.createDirectories(directory);
            temporary=Files.createTempFile(directory,"upload-",".tmp");
            // Re-encode decoded pixels to strip metadata and trailing/polyglot content.
            if(!ImageIO.write(image,extension,temporary.toFile())) throw invalidType();
            if(Files.size(temporary)>maxBytes) throw failure(HttpStatus.PAYLOAD_TOO_LARGE,"AVATAR_TOO_LARGE","Normalized avatar exceeds upload limit");
            String key=UUID.randomUUID()+"."+extension;
            Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch(ApiException ex) { throw ex; }
        catch(javax.imageio.IIOException ex) { throw invalidType(); }
        catch(IOException ex) { throw failure(HttpStatus.SERVICE_UNAVAILABLE,"FILE_STORAGE_UNAVAILABLE","Avatar could not be stored"); }
        finally { if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException ex) { LOG.warn("Unable to clean temporary avatar"); } }
    }
    public Resource read(String key) {
        Path file=path(key);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw failure(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND","Avatar not found");
        return new FileSystemResource(file);
    }
    public void delete(String key) {
        if(key==null) return;
        try { Files.deleteIfExists(path(key)); } catch(IOException ex) { LOG.warn("Unable to remove unused avatar"); }
    }
    private Path path(String key) {
        if(key==null || !key.matches("[0-9a-f-]{36}\\.(png|jpg)")) throw failure(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND","Avatar not found");
        Path result=directory.resolve(key).normalize();
        if(!result.startsWith(directory)) throw invalidType();
        return result;
    }
    private ApiException invalidType() { return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"UNSUPPORTED_AVATAR_TYPE","Avatar must be a supported image"); }
    private ApiException failure(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
}
