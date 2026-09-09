package server;

import database.AuthUser;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.List;

/** Authenticates the existing opaque cube_session token against the existing session table. */
final class SpringSessionAuthenticationProvider implements AuthenticationProvider {
    private final SpringAuthService authService;

    SpringSessionAuthenticationProvider(SpringAuthService authService) {
        this.authService = authService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var token = String.valueOf(authentication.getCredentials());
        try {
            AuthUser user = authService.authenticate(token);
            if (user == null) {
                throw new BadCredentialsException("Invalid session");
            }
            var authority = new SimpleGrantedAuthority("ROLE_" + user.role().toUpperCase(java.util.Locale.ROOT));
            return new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
        } catch (BadCredentialsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException("Unable to authenticate session", exception);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
