package com.transfer.config;

import com.transfer.common.PasswordUtils;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.UserAccount;
import com.transfer.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoDataInitializer {

    @Bean
    CommandLineRunner seedUsers(UserAccountRepository userAccountRepository) {
        return args -> {
            createUserIfMissing(userAccountRepository, "field01", "Field Officer", UserRole.FIELD_OFFICER, "13800000001", "field01@example.com");
            createUserIfMissing(userAccountRepository, "command01", "Command Center", UserRole.COMMAND_CENTER, "13800000002", "command01@example.com");
            createUserIfMissing(userAccountRepository, "rescue01", "Rescue Worker", UserRole.RESCUE_WORKER, "13800000003", "rescue01@example.com");
            createUserIfMissing(userAccountRepository, "admin", "System Admin", UserRole.ADMIN, "13800000004", "admin@example.com");
        };
    }

    private void createUserIfMissing(
            UserAccountRepository repository,
            String username,
            String fullName,
            UserRole role,
            String phone,
            String email
    ) {
        if (repository.existsByUsername(username)) {
            return;
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setStatus(UserStatus.ENABLED);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtils.sha256("123456"));
        repository.save(user);
    }
}
