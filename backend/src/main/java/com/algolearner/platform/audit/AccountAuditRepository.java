package com.algolearner.platform.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuditRepository extends JpaRepository<AccountAuditEntity, Long> { }
