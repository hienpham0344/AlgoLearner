package com.algolearner.platform.profile.avatar;

import com.algolearner.platform.profile.AvatarStorage;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in local-to-private migration. Retains local originals for operator-controlled cleanup. */
@Service
@Profile("!mock")
public class AvatarMigrationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AvatarStorage avatars;
    private final Clock clock;

    public AvatarMigrationService(JdbcTemplate jdbc,PlatformTransactionManager manager,AvatarStorage avatars,Clock clock) {
        this.jdbc=jdbc;this.avatars=avatars;this.clock=clock;
        this.transactions=new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean migrate(UUID accountId) {
        var keys=jdbc.query("select avatar_key from tbl_account where id=? and role in ('STUDENT','TEACHER')",
                (row,n)->row.getString(1),accountId);
        if(keys.isEmpty()||!avatars.legacyKey(keys.getFirst()))return false;
        String oldKey=keys.getFirst();
        // AvatarStorage scans, normalizes, uploads and verifies the remote SHA-256 before CAS.
        String newKey=avatars.migrateLegacy(oldKey);
        try {
            boolean changed=Boolean.TRUE.equals(transactions.execute(status->jdbc.update(
                    "update tbl_account set avatar_key=?,updated_at=? where id=? and avatar_key=?",
                    newKey,Timestamp.from(clock.instant()),accountId,oldKey)==1));
            if(!changed)avatars.delete(newKey);
            return changed;
        } catch(RuntimeException failure) {
            avatars.delete(newKey);
            throw failure;
        }
    }
    public int migrateBatch(int limit) {
        if(limit<1||limit>1000)throw new IllegalArgumentException("Avatar migration batch must be between 1 and 1000");
        var accounts=jdbc.query("""
                select id from tbl_account where role in ('STUDENT','TEACHER')
                  and avatar_key ~ '^[0-9a-f-]{36}\\.(png|jpg)$' order by id limit ?
                """,(row,n)->row.getObject(1,UUID.class),limit);
        int changed=0;
        for(var account:accounts)if(migrate(account))changed++;
        return changed;
    }
}
