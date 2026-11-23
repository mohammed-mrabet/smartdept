package ma.ump.smartdept.security;

import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Utilisateur connecté, tel que Spring Security le voit (identifiant, e-mail, rôle). */
public record AuthUser(Long id, String email, String passwordHash, Role role, boolean enabled) implements UserDetails {

    public static AuthUser of(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isEnabled());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
