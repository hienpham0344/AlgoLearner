package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.auth.AccountRepository;
import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.common.security.AccountAccess;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fixed active Admin role grants only these grading operations in the separate administration context. */
@Service
@Profile("!mock")
@Transactional(readOnly=true)
public class AssignmentGradingAccess {
    enum Permission {
        REJUDGE(true), READ_HISTORY(false), CHANGE_CODE_CASE_WEIGHTS(true);
        final boolean mutation;
        Permission(boolean mutation){this.mutation=mutation;}
    }
    private final JdbcTemplate jdbc;
    private final AccountAccess access;
    private final AccountRepository accounts;
    public AssignmentGradingAccess(JdbcTemplate jdbc,AccountAccess access,AccountRepository accounts) {
        this.jdbc=jdbc;this.access=access;this.accounts=accounts;
    }
    AccountEntity requireAdmin(UUID actor,Permission permission) {
        if(permission==null)throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        if(permission.mutation && jdbc.queryForList("SELECT id FROM tbl_account WHERE id=? FOR SHARE",UUID.class,actor).isEmpty())
            throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        if(!"ADMIN".equals(access.activeRole(actor)))throw new ApiException(ErrorCode.FORBIDDEN_OPERATION);
        return accounts.findById(actor).orElseThrow(()->new ApiException(ErrorCode.FORBIDDEN_OPERATION));
    }
    static String reason(String value) {
        if(value==null || value.isBlank() || value.strip().length()>500)throw new ApiException(ErrorCode.VALIDATION_FAILED,"A reason of up to 500 characters is required");
        return value.strip();
    }
}
