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
            createUserIfMissing(userAccountRepository, "police1", "张警官", UserRole.FIELD_OFFICER, "13800000001", "police1@example.com");
            createUserIfMissing(userAccountRepository, "command1", "李指挥", UserRole.COMMAND_CENTER, "13800000002", "command1@example.com");
            createUserIfMissing(userAccountRepository, "rescue1", "王队长", UserRole.RESCUE_WORKER, "13800000003", "rescue1@example.com");
            createUserIfMissing(userAccountRepository, "admin1", "赵管理", UserRole.ADMIN, "13800000004", "admin1@example.com");
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
