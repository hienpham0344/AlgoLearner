package com.algolearner.platform.profile;

import com.algolearner.platform.common.security.AccountAccess;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    final UUID id=UUID.randomUUID();
    final Clock clock=Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"),ZoneOffset.UTC);
    final ProfileRepository repository=mock(ProfileRepository.class);
    final AvatarStorage storage=mock(AvatarStorage.class);
    final AccountAccess access=mock(AccountAccess.class);
    final PracticeStatisticsService statistics=mock(PracticeStatisticsService.class);
    final ProfileService service=new ProfileService(repository,statistics,storage,access,clock);
    @BeforeEach void setup() {
        when(access.activeRole(id)).thenReturn("STUDENT");
        when(repository.own(id)).thenReturn(new ProfileRepository.Profile(id,"Name","student","s@example.test","old.png","Bio","STUDENT",clock.instant(),false));
    }
    @Test void storageFailurePreservesOldAvatar() {
        var file=new MockMultipartFile("file",new byte[]{1});
        var failure=new IllegalStateException("storage offline");
        when(storage.store(file)).thenThrow(failure);
        assertThatThrownBy(() -> service.avatar(id,file)).isSameAs(failure);
        verify(repository,never()).avatar(any(),any(),any());
        verify(storage,never()).delete(any());
    }
    @Test void databaseFailureCleansNewAvatarAndPreservesOld() {
        var file=new MockMultipartFile("file",new byte[]{1});
        when(storage.store(file)).thenReturn("new.png");
        var failure=new IllegalStateException("write failed");
        doThrow(failure).when(repository).avatar(id,"new.png",clock.instant());
        assertThatThrownBy(() -> service.avatar(id,file)).isSameAs(failure);
        verify(storage).delete("new.png");
        verify(storage,never()).delete("old.png");
    }
    @Test void partialUpdatePreservesOmittedBioAndClearsBlankName() {
        when(statistics.forAccount(id)).thenReturn(new PracticeStatisticsService.Statistics(0,new PracticeStatisticsService.Streak(0,0)));
        var request=new ProfileDtos.ProfileUpdate();
        request.fullName("  ");
        service.update(id,request);
        verify(repository).update(id,null,"Bio",clock.instant());
    }
    @Test void unicodeLengthBoundaryUsesCodePoints() {
        when(statistics.forAccount(id)).thenReturn(new PracticeStatisticsService.Statistics(0,new PracticeStatisticsService.Streak(0,0)));
        var request=new ProfileDtos.ProfileUpdate();
        request.fullName("😀".repeat(150));
        service.update(id,request);
        request.fullName("😀".repeat(151));
        assertThatThrownBy(() -> service.update(id,request)).isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
        verify(repository,times(1)).update(any(),any(),any(),any());
    }
}
