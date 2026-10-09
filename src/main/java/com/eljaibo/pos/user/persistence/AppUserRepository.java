package com.eljaibo.pos.user.persistence;

import com.eljaibo.pos.user.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.eljaibo.pos.user.domain.UserRole;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RepositoryRestResource(exported = false)
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where lower(u.username) = lower(:username)")
    Optional<AppUser> findForLogin(@Param("username") String username);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id and u.business.id = :businessId")
    Optional<AppUser> findForPasswordChange(@Param("id") UUID id, @Param("businessId") UUID businessId);

    List<AppUser> findAllByBusinessIdOrderByUsernameAsc(UUID businessId);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<AppUser> findByIdAndBusinessId(UUID id, UUID businessId);

    long countByBusinessIdAndRoleAndActiveTrue(UUID businessId, UserRole role);

    // A projection avoids loading an entity and issuing a second query for its business.
    @Query("""
            select u.id as id, u.business.id as businessId, u.role as role, u.credentialVersion as credentialVersion
            from AppUser u join u.business b
            where u.id = :id and b.id = :businessId and u.active = true and b.active = true
            """)
    Optional<ActiveIdentity> findActiveIdentity(@Param("id") UUID id,
                                               @Param("businessId") UUID businessId);

    interface ActiveIdentity {
        UUID getId();
        UUID getBusinessId();
        UserRole getRole();
        long getCredentialVersion();
    }

    @Query("""
            select u.id as id, b.id as businessId, u.role as role, u.credentialVersion as credentialVersion
            from AppUser u join u.business b
            where u.id in :ids and u.active = true and b.active = true
            """)
    List<ActiveIdentity> findActiveIdentities(@Param("ids") java.util.Collection<UUID> ids);
}
