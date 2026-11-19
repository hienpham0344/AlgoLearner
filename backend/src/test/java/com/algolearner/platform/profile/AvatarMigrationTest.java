package com.algolearner.platform.profile;

import com.algolearner.platform.auth.*;
import com.algolearner.platform.storage.*;
import com.algolearner.platform.support.PostgresIntegrationTest;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"logging.level.root=WARN","debug=false","app.security.jwt-secret=avatar-migration-test-only-secret-at-least-32-bytes","app.mail.from=sender@example.test"})
@ActiveProfiles({"internal-auth","test"})
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches="jdbc:postgresql:.*")
class AvatarMigrationTest extends PostgresIntegrationTest {
    @TempDir Path directory;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.context.ApplicationContext context;
    String oldKey;UUID account;
    final Map<String,byte[]> remote=new HashMap<>();
    AvatarStorage storage(Runnable beforeVerification)throws Exception {
        var objectStore=new PrivateObjectStorage() {
            public void upload(String key,String type,byte[] bytes){remote.put(key,bytes.clone());beforeVerification.run();}
            public void download(String key,OutputStream out)throws IOException {out.write(remote.get(key));}
            public void delete(String key){remote.remove(key);}
        };
        return new AvatarStorage(directory.toString(),5_242_880,16_000_000,"image/png,image/jpeg",
                AvatarRemoteStorageTest.provider(PrivateObjectStorage.class,objectStore),AvatarRemoteStorageTest.provider(MalwareScanner.class,bytes->{}));
    }
    void account()throws Exception {
        account=UUID.randomUUID();oldKey=UUID.randomUUID()+".png";
        var a=new AccountEntity();a.setId(account);a.setUsername("avatar_"+account.toString().replace("-",""));a.setEmail(a.getUsername()+"@example.test");a.setPasswordHash("unused");a.setRole(AccountRole.STUDENT);a.setStatus(AccountStatus.ACTIVE);a.setAvatarKey(oldKey);accounts.saveAndFlush(a);
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",directory.resolve(oldKey).toFile());
    }
    com.algolearner.platform.profile.avatar.AvatarMigrationService migration(AvatarStorage storage) {
        return new com.algolearner.platform.profile.avatar.AvatarMigrationService(jdbc,transactions,storage,Clock.systemUTC());
    }
    boolean migrate(com.algolearner.platform.profile.avatar.AvatarMigrationService service) {return service.migrate(account);}
    @Test void verifiedMigrationIsIdempotentAndRetainsLegacyUntilOperatorCleanup()throws Exception {
        account();var service=migration(storage(()->{}));assertThat(migrate(service)).isTrue();
        var key=jdbc.queryForObject("select avatar_key from tbl_account where id=?",String.class,account);
        assertThat(key).contains("/");assertThat(remote).containsKey(key);assertThat(directory.resolve(oldKey)).exists();
        assertThat(migrate(service)).isFalse();assertThat(remote).hasSize(1);
        assertThat(context.containsBean("avatarMigrationRunner")).isFalse();
    }
    @Test void concurrentReplacementWinsCasAndNewMigrationObjectIsCompensated()throws Exception {
        account();String replacement=UUID.randomUUID()+"/"+UUID.randomUUID();
        var service=migration(storage(()->jdbc.update("update tbl_account set avatar_key=? where id=?",replacement,account)));
        assertThat(migrate(service)).isFalse();assertThat(remote).isEmpty();assertThat(directory.resolve(oldKey)).exists();
        assertThat(jdbc.queryForObject("select avatar_key from tbl_account where id=?",String.class,account)).isEqualTo(replacement);
    }
    @Test void databaseFailureRollsBackAndCompensatesRemoteWithoutDeletingLegacy()throws Exception {
        account();
        jdbc.execute("create function reject_avatar_migration() returns trigger language plpgsql as $$ begin raise exception 'Avatar migration fixture rejection'; end $$");
        jdbc.execute("create trigger reject_avatar_migration before update of avatar_key on tbl_account for each row when (NEW.id=\'"+account+"\'::uuid) execute function reject_avatar_migration()");
        var service=migration(storage(()->{}));
        assertThatThrownBy(()->migrate(service)).isInstanceOf(RuntimeException.class);
        assertThat(remote).isEmpty();assertThat(directory.resolve(oldKey)).exists();
        assertThat(jdbc.queryForObject("select avatar_key from tbl_account where id=?",String.class,account)).isEqualTo(oldKey);
    }
}
