package com.carddemo.config;

import com.carddemo.domain.AppUser;
import com.carddemo.repository.AppUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Establishes the request principal from the bearer token.
 *
 * <p>FR-AUTH-005 / target enforcement rule 3: the role is re-read from the database on every
 * request rather than trusted from the token payload, so a role change or a disabled account takes
 * effect immediately.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim());
            if (claims != null) {
                Optional<AppUser> found = users.findById(claims.getSubject());
                if (found.isPresent() && isUsable(found.get())) {
                    AppUser user = found.get();
                    String authority = user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER";
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user.getUserId(), null,
                                    List.of(new SimpleGrantedAuthority(authority)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isUsable(AppUser user) {
        if (!user.isActive()) {
            return false;
        }
        return user.getLockedUntil() == null || user.getLockedUntil().isBefore(LocalDateTime.now());
    }
}
