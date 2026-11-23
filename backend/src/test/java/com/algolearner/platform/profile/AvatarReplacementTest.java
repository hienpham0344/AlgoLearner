package com.algolearner.platform.profile;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.common.exception.AppException;
import com.algolearner.platform.common.security.AccountAccess;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"logging.level.root=WARN","debug=false","app.security.jwt-secret=avatar-replacement-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class AvatarReplacementTest extends PostgresIntegrationTest {
    @Autowired ProfileRepository repository;
    @Autowired AccountRepository accounts;
    UUID account(String old) {
        var a=new AccountEntity();a.setId(UUID.randomUUID());a.setUsername("avatar_"+a.getId().toString().replace("-",""));a.setEmail(a.getUsername()+"@example.test");a.setPasswordHash("unused");a.setRole(AccountRole.STUDENT);a.setStatus(AccountStatus.ACTIVE);a.setAvatarKey(old);return accounts.saveAndFlush(a).getId();
    }
    ProfileService service(AvatarStorage storage) {
        var access=mock(AccountAccess.class);when(access.activeRole(any())).thenReturn("STUDENT");
        var statistics=mock(PracticeStatisticsService.class);when(statistics.forAccount(any())).thenReturn(new PracticeStatisticsService.Statistics(0,new PracticeStatisticsService.Streak(0,0)));
        return new ProfileService(repository,statistics,storage,access,Clock.systemUTC());
    }
    @Test void independentAccountsDoNotSerializeRemoteIoGlobally()throws Exception {
        UUID left=account(null),right=account(null);var storage=mock(AvatarStorage.class);var barrier=new CyclicBarrier(2);
        when(storage.store(any())).thenAnswer(call->{barrier.await(3,TimeUnit.SECONDS);return UUID.randomUUID()+"/"+UUID.randomUUID();});
        var service=service(storage);var file=new MockMultipartFile("file",new byte[]{1});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->service.avatar(left,file));var two=pool.submit(()->service.avatar(right,file));
            assertThat(one.get(10,TimeUnit.SECONDS)).isNotNull();assertThat(two.get(10,TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(repository.own(left).avatarKey()).contains("/");assertThat(repository.own(right).avatarKey()).contains("/");
    }
    @Test void sameAccountRaceHasOneWinnerAndCompensatesOnlyLoserBlob()throws Exception {
        String old=UUID.randomUUID()+".png";UUID id=account(old);var storage=mock(AvatarStorage.class);var barrier=new CyclicBarrier(2);
        var generated=new ConcurrentLinkedQueue<String>();
        when(storage.store(any())).thenAnswer(call->{String key=UUID.randomUUID()+"/"+UUID.randomUUID();generated.add(key);barrier.await(3,TimeUnit.SECONDS);return key;});
        var service=service(storage);var file=new MockMultipartFile("file",new byte[]{1});var results=new ArrayList<Object>();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Object> replace=()->{try{return service.avatar(id,file);}catch(RuntimeException failure){return failure;}};
            var one=pool.submit(replace);var two=pool.submit(replace);results.add(one.get(10,TimeUnit.SECONDS));results.add(two.get(10,TimeUnit.SECONDS));
        }
        assertThat(results.stream().filter(ProfileDtos.UserProfile.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(AppException.class::isInstance).map(AppException.class::cast).map(e->e.getHttpStatus().value())).containsExactly(409);
        String winning=repository.own(id).avatarKey();verify(storage,never()).delete(winning);verify(storage).delete(old);
        generated.stream().filter(key->!key.equals(winning)).forEach(key->verify(storage).delete(key));
    }
}
