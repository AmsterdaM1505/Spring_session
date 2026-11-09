package ru.mephi.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.booking.domain.AppUser;
import ru.mephi.booking.domain.AppUser.Role;
import ru.mephi.booking.repository.UserRepository;

@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DemoAccounts implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String adminPassword;
    private final String userPassword;
    public DemoAccounts(UserRepository users, PasswordEncoder passwords,
    @Value("${app.admin-password}") String adminPassword, @Value("${app.user-password}") String userPassword) {
        this.users = users;
        this.passwords = passwords;
        this.adminPassword = adminPassword;
        this.userPassword = userPassword;
    }
    @Override
    @Transactional public void run(ApplicationArguments args) {
        if (users.count() != 0) return;
        users.save(new AppUser("admin", passwords.encode(adminPassword), Role.ADMIN));
        users.save(new AppUser("student", passwords.encode(userPassword), Role.USER));
        users.save(new AppUser("student2", passwords.encode(userPassword), Role.USER));
    }
}
