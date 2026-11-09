package ru.mephi.booking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {
    public enum Role {
        USER, ADMIN
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 64) private String username;
    @Column(nullable = false, length = 100) private String password;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Role role;
    @Column(nullable = false) private boolean active = true;
    @Version private long version;
    public AppUser(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }
    public void update(String username, String password, Role role) {
        if (username != null) this.username = username;
        if (password != null) this.password = password;
        if (role != null) this.role = role;
    }
    public void deactivate() {
        active = false;
    }
}
