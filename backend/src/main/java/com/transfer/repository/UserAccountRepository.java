package com.transfer.repository;

import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long> {

    boolean existsByUsername(String username);

    Optional<UserAccount> findByUsername(String username);

    long countByStatus(UserStatus status);

    long countByRole(UserRole role);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    List<UserAccount> findByRoleInAndStatusOrderByFullNameAsc(
            Collection<UserRole> roles,
            UserStatus status
    );
}
