package com.transfer.config;

import com.transfer.common.PasswordUtils;
import com.transfer.enums.SystemDataCategory;
import com.transfer.enums.UserRole;
import com.transfer.enums.UserStatus;
import com.transfer.model.SystemData;
import com.transfer.model.UserAccount;
import com.transfer.repository.SystemDataRepository;
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

    @Bean
    CommandLineRunner seedSystemData(SystemDataRepository systemDataRepository) {
        return args -> {
            createSystemDataIfMissing(systemDataRepository, SystemDataCategory.ROAD, "EXPRESSWAY", "高速公路", "{\"level\":\"HIGHWAY\",\"defaultSpeedLimit\":120}", "道路基础数据示例", 10);
            createSystemDataIfMissing(systemDataRepository, SystemDataCategory.ROAD, "URBAN_MAIN", "城市主干路", "{\"level\":\"ARTERIAL\",\"defaultSpeedLimit\":60}", "道路基础数据示例", 20);
            createSystemDataIfMissing(systemDataRepository, SystemDataCategory.ACCIDENT_TYPE, "REAR_END", "追尾事故", "{\"defaultRisk\":\"MEDIUM\"}", "事故类型字典示例", 10);
            createSystemDataIfMissing(systemDataRepository, SystemDataCategory.ACCIDENT_TYPE, "ROLLOVER", "车辆侧翻", "{\"defaultRisk\":\"HIGH\"}", "事故类型字典示例", 20);
            createSystemDataIfMissing(systemDataRepository, SystemDataCategory.RISK_RULE, "HIGH_RISK_LANE_BLOCK", "多车道占用高风险规则", "{\"occupiedLanesGte\":2,\"riskLevel\":\"HIGH\"}", "风险等级规则示例", 10);
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

    private void createSystemDataIfMissing(
            SystemDataRepository repository,
            SystemDataCategory category,
            String code,
            String name,
            String value,
            String description,
            Integer sortOrder
    ) {
        if (repository.existsByCategoryAndCode(category, code)) {
            return;
        }
        SystemData data = new SystemData();
        data.setCategory(category);
        data.setCode(code);
        data.setName(name);
        data.setValue(value);
        data.setDescription(description);
        data.setEnabled(true);
        data.setSortOrder(sortOrder);
        repository.save(data);
    }
}
