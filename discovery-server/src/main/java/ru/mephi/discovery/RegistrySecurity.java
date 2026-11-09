package ru.mephi.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class RegistrySecurity {
    @Bean UserDetailsService registryUsers(@Value("${registry.password}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername("registry")
        .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("REGISTRY").build());
    }
    @Bean SecurityFilterChain registry(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.ignoringRequestMatchers("/eureka/**"))
        .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health").permitAll().anyRequest().hasRole("REGISTRY"))
        .httpBasic(Customizer.withDefaults()).build();
    }
}
