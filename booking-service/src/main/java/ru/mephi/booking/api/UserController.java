package ru.mephi.booking.api;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.mephi.booking.api.UserDtos.*;
import ru.mephi.booking.security.TokenService;
import ru.mephi.booking.service.AccountService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final AccountService accounts;
    private final TokenService tokens;
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenView register(@RequestBody @Valid Credentials input) {
        var user = accounts.register(input);
        return new TokenView(tokens.user(user), "Bearer", 3600, accounts.view(user));
    }
    @PostMapping("/auth") public TokenView login(@RequestBody @Valid Credentials input) {
        var user = accounts.authenticate(input);
        return new TokenView(tokens.user(user), "Bearer", 3600, accounts.view(user));
    }
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')") public List<UserView> all(@AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return accounts.list();
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')") public UserView get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return accounts.get(id);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserView create(@RequestBody @Valid NewUser input, @AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return accounts.create(input);
    }
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserView update(@PathVariable long id, @RequestBody @Valid UpdateUser input, @AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        return accounts.update(id, input);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        admin(jwt);
        accounts.delete(id);
    }
    private void admin(Jwt jwt) {
        accounts.requireAdmin(Long.parseLong(jwt.getSubject()));
    }
}
