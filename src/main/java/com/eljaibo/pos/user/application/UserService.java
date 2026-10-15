package com.eljaibo.pos.user.application;

import com.eljaibo.pos.user.api.CreateUserRequest;
import com.eljaibo.pos.user.api.ChangePasswordRequest;
import com.eljaibo.pos.user.api.ResetPasswordRequest;
import com.eljaibo.pos.user.domain.UserCredentialsChanged;
import com.eljaibo.pos.user.api.UserResponse;
import com.eljaibo.pos.user.domain.AppUser;
import com.eljaibo.pos.user.domain.UserRole;
import com.eljaibo.pos.user.domain.UserDeactivated;
import org.springframework.context.ApplicationEventPublisher;
import com.eljaibo.pos.user.persistence.AppUserRepository;
import com.eljaibo.pos.auth.security.ValidatedUserIdentity;
import org.springframework.security.core.context.SecurityContextHolder;
import com.eljaibo.pos.business.persistence.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BusinessRepository businesses;
    private final ApplicationEventPublisher events;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder,
                       BusinessRepository businesses, ApplicationEventPublisher events) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.businesses = businesses;
        this.events = events;
    }

    @Transactional
    public void changePassword(UUID userId, UUID businessId, long credentialVersion,
                               ChangePasswordRequest request) {
        lockBusiness(businessId);
        AppUser user = users.findForPasswordChange(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        requireCurrentCredentials(user, credentialVersion);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        updatePassword(userId, businessId, user, request.newPassword());
    }

    @Transactional
    public void resetPassword(UUID administratorId, UUID businessId, long credentialVersion,
                              UUID userId, ResetPasswordRequest request) {
        lockBusiness(businessId);
        requireAdministrator(administratorId, businessId, false);
        AppUser administrator = users.findById(administratorId).orElseThrow();
        requireCurrentCredentials(administrator, credentialVersion);
        if (administratorId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the own-password endpoint");
        }
        AppUser target = users.findForPasswordChange(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        updatePassword(administratorId, businessId, target, request.newPassword());
    }

    private void lockBusiness(UUID businessId) {
        businesses.findForUserAdministration(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active business required"));
    }

    private void requireCurrentCredentials(AppUser user, long version) {
        if (!user.isActive() || user.getCredentialVersion() != version) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
    }

    private void updatePassword(UUID actorId, UUID businessId, AppUser user, String password) {
        user.changePassword(passwordEncoder.encode(password));
        events.publishEvent(new UserCredentialsChanged(actorId, businessId, user.getId(), user.getCredentialVersion()));
        log.info("User credentials changed: actorId={}, userId={}, businessId={}, credentialVersion={}",
                actorId, user.getId(), businessId, user.getCredentialVersion());
    }

    @Transactional
    public UserResponse activate(UUID administratorId, UUID businessId, UUID userId) {
        return changeActive(administratorId, businessId, userId, true);
    }

    @Transactional
    public UserResponse deactivate(UUID administratorId, UUID businessId, UUID userId) {
        return changeActive(administratorId, businessId, userId, false);
    }

    private UserResponse changeActive(UUID administratorId, UUID businessId, UUID userId, boolean active) {
        // Lock before loading either user, so a waiting administrator cannot act on stale state.
        businesses.findForUserAdministration(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active business required"));
        // Always refresh after the lock; a request identity can become stale while waiting.
        requireAdministrator(administratorId, businessId, false);
        AppUser target = users.findByIdAndBusinessId(userId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!active && administratorId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivar tu propia cuenta");
        }
        if (!active && target.isActive() && target.getRole() == UserRole.ADMIN
                && users.countByBusinessIdAndRoleAndActiveTrue(businessId, UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active administrator cannot be deactivated");
        }
        boolean changed = target.isActive() != active;
        if (active) target.activate();
        else target.deactivate();
        if (!active) events.publishEvent(new UserDeactivated(administratorId, businessId, userId));
        log.info("User activation updated: administratorId={}, userId={}, businessId={}, active={}, changed={}",
                administratorId, userId, businessId, active, changed);
        return UserResponse.from(target);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(UUID userId, UUID businessId) {
        requireAdministrator(userId, businessId, true);
        return users.findAllByBusinessIdOrderByUsernameAsc(businessId)
                .stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(UUID userId, UUID businessId, CreateUserRequest request) {
        requireAdministrator(userId, businessId, true);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw duplicateUsername();
        }

        AppUser user = new AppUser(businesses.getReferenceById(businessId), username,
                passwordEncoder.encode(request.password()), request.displayName(), request.role());
        try {
            // Flush here so a concurrent duplicate is reported as 409 before returning the DTO.
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "uq_app_user_username_lower".equals(violation.getConstraintName())) {
                    throw duplicateUsername();
                }
            }
            throw exception;
        }
    }

    private void requireAdministrator(UUID userId, UUID businessId, boolean reuseRequestIdentity) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (reuseRequestIdentity && authentication != null
                && authentication.getDetails() instanceof ValidatedUserIdentity identity
                && identity.userId().equals(userId) && identity.businessId().equals(businessId)
                && identity.role() == UserRole.ADMIN) {
            return;
        }
        // Direct service calls and mutations after locking need a fresh transactional check.
        users.findById(userId)
                .filter(user -> user.isActive() && user.getRole() == UserRole.ADMIN
                        && user.getBusiness().getId().equals(businessId)
                        && user.getBusiness().isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "An active administrator from this business is required"));
    }

    private ResponseStatusException duplicateUsername() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }
}
