package com.eljaibo.pos.business.persistence;

import com.eljaibo.pos.business.domain.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    Optional<Business> findFirstByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.rest.core.annotation.RestResource(exported = false)
    @Query("select b from Business b where b.id = :id and b.active = true")
    Optional<Business> findForUserAdministration(@Param("id") UUID id);
}
