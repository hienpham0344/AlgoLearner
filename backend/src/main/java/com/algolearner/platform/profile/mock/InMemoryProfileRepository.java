package com.algolearner.platform.profile.mock;
import com.algolearner.platform.profile.ProfileRepository;
import com.algolearner.platform.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.UUID;
@Repository
@org.springframework.context.annotation.Profile("mock")
public class InMemoryProfileRepository implements ProfileRepository {
    private final MockData data;
    public InMemoryProfileRepository(MockData data) { this.data=data; }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"PROFILE_NOT_FOUND","Profile not found"); }
    public Profile own(UUID id) {
        Profile p=data.profiles.get(id);
        if(p==null || !java.util.Set.of("STUDENT","TEACHER").contains(p.role())) throw missing();
        return p;
    }
    public Profile byUsername(String username) { return data.profiles.values().stream().filter(p -> p.username().equals(username)).findFirst().orElseThrow(this::missing); }
    public Profile publicProfile(String username,Instant now) {
        Profile p=byUsername(username);
        boolean active=data.accounts.stream().anyMatch(a -> a.id().equals(p.accountId()) && a.status().equals("ACTIVE"));
        boolean suspended=data.restrictions.stream().anyMatch(r -> r.accountId().equals(p.accountId()) && r.kind().equals("SUSPENSION") && r.active(now));
        if(!p.isPublic() || !active || suspended || !java.util.Set.of("STUDENT","TEACHER").contains(p.role())) throw missing();
        return p;
    }
    public void update(UUID id,String name,String bio,Instant now) {
        data.profiles.compute(id,(key,p) -> { if(p==null) throw missing(); return copy(p,name,bio,p.avatarKey(),p.isPublic()); });
    }
    public void privacy(UUID id,boolean visible,Instant now) {
        data.profiles.compute(id,(key,p) -> { if(p==null) throw missing(); return copy(p,p.fullName(),p.shortBio(),p.avatarKey(),visible); });
    }
    public void avatar(UUID id,String avatar,Instant now) {
        data.profiles.compute(id,(key,p) -> { if(p==null) throw missing(); return copy(p,p.fullName(),p.shortBio(),avatar,p.isPublic()); });
    }
    private Profile copy(Profile p,String name,String bio,String avatar,boolean visible) {
        return new Profile(p.accountId(),name,p.username(),p.email(),avatar,bio,p.role(),p.joinedAt(),visible);
    }
}
