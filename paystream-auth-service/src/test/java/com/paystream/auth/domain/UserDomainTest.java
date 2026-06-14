package com.paystream.auth.domain;

import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.User;
import com.paystream.auth.domain.model.UserStatus;
import com.paystream.common.util.IdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserDomainTest {

    @Test
    void isActive_returnsTrueForActiveStatus() {
        User user = activeUser();
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void isLocked_returnsFalseBeforeAnyFailures() {
        User user = activeUser();
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    void recordFailedLogin_incrementsCounter() {
        User user = activeUser();
        user.recordFailedLogin();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    void recordFailedLogin_5Times_locksAccount() {
        User user = activeUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin();
        }
        assertThat(user.isLocked()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void resetLoginAttempts_clearsLockout() {
        User user = activeUser();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin();
        }
        assertThat(user.isLocked()).isTrue();

        user.resetLoginAttempts();

        assertThat(user.isLocked()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void recordFailedLogin_4Times_doesNotLock() {
        User user = activeUser();
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin();
        }
        assertThat(user.isLocked()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
    }

    @Test
    void isActive_returnsFalseForSuspendedUser() {
        User user = new User(IdGenerator.generate(), "x@x.com", "hash", "Name",
                Role.CUSTOMER, UserStatus.SUSPENDED, 0, null, 0);
        assertThat(user.isActive()).isFalse();
    }

    // -------------------------------------------------------------------------

    private User activeUser() {
        return new User(IdGenerator.generate(), "user@paystream.com",
                "$2a$12$hash", "Test User", Role.CUSTOMER,
                UserStatus.ACTIVE, 0, null, 0);
    }
}
