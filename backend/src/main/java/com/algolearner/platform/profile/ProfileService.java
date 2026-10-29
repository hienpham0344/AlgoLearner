package com.algolearner.platform.profile;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.security.AccountAccess;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import static com.algolearner.platform.profile.ProfileDtos.*;

@Service
public class ProfileService {
    private final ProfileRepository repository;
    private final PracticeStatisticsService statistics;
    private final AvatarStorage storage;
    private final AccountAccess access;
    private final Clock clock;
    public ProfileService(ProfileRepository repository,PracticeStatisticsService statistics,AvatarStorage storage,AccountAccess access,Clock clock) {
        this.repository=repository; this.statistics=statistics; this.storage=storage; this.access=access; this.clock=clock;
    }
    public UserProfile own(UUID id) { requireProfileRole(id); return ownResponse(repository.own(id)); }
    public synchronized UserProfile update(UUID id,ProfileUpdate request) {
        requireProfileRole(id);
        if(!request.hasFullName && !request.hasShortBio) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST_BODY","At least one editable field is required");
        String name=normalize(request.fullName,150),bio=normalize(request.shortBio,500);
        var old=repository.own(id);
        repository.update(id,request.hasFullName?name:old.fullName(),request.hasShortBio?bio:old.shortBio(),clock.instant());
        return ownResponse(repository.own(id));
    }
    public synchronized UserProfile privacy(UUID id,PrivacyUpdate request) {
        requireProfileRole(id);
        if(request.isPublic()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","isPublic must be a boolean");
        repository.privacy(id,request.isPublic(),clock.instant());
        return ownResponse(repository.own(id));
    }
    public synchronized UserProfile avatar(UUID id,MultipartFile file) {
        requireProfileRole(id);
        String oldKey=repository.own(id).avatarKey();
        String newKey=storage.store(file);
        try {
            repository.avatar(id,newKey,clock.instant());
        } catch (RuntimeException ex) {
            storage.delete(newKey);
            throw ex;
        }
        storage.delete(oldKey);
        return ownResponse(repository.own(id));
    }
    public PublicUserProfile publicProfile(String username) {
        var p=repository.publicProfile(username,clock.instant());
        var stats=statistics.forAccount(p.accountId());
        return new PublicUserProfile(p.username(),avatarUrl(p),p.shortBio(),stats.solved(),stats.streak());
    }
    public Resource avatarContent(String username,UUID viewer) {
        var p=repository.byUsername(username);
        if(viewer==null || !viewer.equals(p.accountId())) p=repository.publicProfile(username,clock.instant());
        else requireProfileRole(viewer);
        return storage.read(p.avatarKey());
    }
    private UserProfile ownResponse(ProfileRepository.Profile p) {
        var stats=statistics.forAccount(p.accountId());
        return new UserProfile(p.fullName(),p.username(),p.email(),avatarUrl(p),p.shortBio(),p.role(),p.joinedAt(),p.isPublic(),new PublicPracticeStatistics(stats.solved()),stats.streak());
    }
    private String avatarUrl(ProfileRepository.Profile p) {
        return p.avatarKey()==null?null:"/api/v1/users/"+UriUtils.encodePathSegment(p.username(),StandardCharsets.UTF_8)+"/profile/avatar";
    }
    private void requireProfileRole(UUID id) {
        if(!java.util.Set.of("STUDENT","TEACHER").contains(access.activeRole(id)))
            throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN_OPERATION","Profile management is available to Student and Teacher only");
    }
    private String normalize(String value,int limit) {
        if(value==null) return null;
        String result=value.strip();
        if(result.codePointCount(0,result.length())>limit) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Profile text exceeds maximum length");
        return result.isEmpty()?null:result;
    }
}
