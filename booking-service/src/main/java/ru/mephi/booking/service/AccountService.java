package ru.mephi.booking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.booking.api.UserDtos.*;
import ru.mephi.booking.domain.AppUser;
import ru.mephi.booking.domain.AppUser.Role;
import ru.mephi.booking.repository.UserRepository;
import ru.mephi.shared.DomainException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    @Transactional public AppUser register(Credentials input) {
        return create(input.username(), input.password(), Role.USER);
    }
    @Transactional public UserView create(NewUser input) {
        return view(create(input.username(), input.password(), input.role()));
    }
    private AppUser create(String username, String password, Role role) {
        if (users.existsByUsername(username)) throw new DomainException(409, "USERNAME_TAKEN", "Имя пользователя занято");
        return users.saveAndFlush(new AppUser(username, encode(password), role));
    }
    public AppUser authenticate(Credentials input) {
        AppUser user = users.findByUsername(input.username()).filter(AppUser::isActive)
        .orElseThrow(this::badCredentials);
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !passwords.matches(input.password(), user.getPassword())) throw badCredentials();
        return user;
    }
    public AppUser current(long userId) {
        return users.findById(userId).filter(AppUser::isActive)
        .orElseThrow(() -> new DomainException(401, "ACCOUNT_INACTIVE", "Учётная запись недоступна"));
    }
    public void requireAdmin(long id) {
        if (current(id).getRole() != Role.ADMIN) throw new DomainException(403, "FORBIDDEN", "Недостаточно прав");
    }
    public List<UserView> list() {
        return users.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(this::view).toList();
    }
    public UserView get(long id) {
        return view(require(id));
    }
    @Transactional public UserView update(long id, UpdateUser input) {
        AppUser user = users.lockById(id).orElseThrow(this::missing);
        if (input.username() != null && !input.username().equals(user.getUsername()) && users.existsByUsername(input.username())) {
            throw new DomainException(409, "USERNAME_TAKEN", "Имя пользователя занято");
        }
        user.update(input.username(), input.password() == null ? null : encode(input.password()), input.role());
        return view(user);
    }
    @Transactional public void delete(long id) {
        users.lockById(id).orElseThrow(this::missing).deactivate();
    }
    public UserView view(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getRole(), user.isActive());
    }
    private String encode(String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new DomainException(400, "PASSWORD_TOO_LONG", "Пароль должен занимать не более 72 байт UTF-8");
        }
        return passwords.encode(password);
    }
    private AppUser require(long id) {
        return users.findById(id).orElseThrow(this::missing);
    }
    private DomainException missing() {
        return new DomainException(404, "USER_NOT_FOUND", "Пользователь не найден");
    }
    private DomainException badCredentials() {
        return new DomainException(401, "BAD_CREDENTIALS", "Неверное имя пользователя или пароль");
    }
}
