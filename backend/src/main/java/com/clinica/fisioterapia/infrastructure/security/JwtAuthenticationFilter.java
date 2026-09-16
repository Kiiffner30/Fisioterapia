package com.clinica.fisioterapia.infrastructure.security;

import com.clinica.fisioterapia.application.auth.TokenProvider;
import com.clinica.fisioterapia.domain.user.RoleName;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Filtro de autenticacion JWT (stateless).
 * <ul>
 *   <li>Rutas publicas (login/refresh/logout): pasa sin autenticar.</li>
 *   <li>Rutas protegidas: exige "Authorization: Bearer &lt;accessToken&gt;".
 *       Si el token falta o es invalido responde 401 JSON.</li>
 * </ul>
 * La autorizacion por perfil se resuelve despues, en Spring Security
 * (anyRequest().authenticated() + @PreAuthorize con roles).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;
    private final HttpErrorWriter errorWriter;
    private final List<String> publicPaths;

    public JwtAuthenticationFilter(TokenProvider tokenProvider, HttpErrorWriter errorWriter,
                                   List<String> publicPaths) {
        this.tokenProvider = tokenProvider;
        this.errorWriter = errorWriter;
        this.publicPaths = publicPaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return publicPaths.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        Optional<TokenProvider.Claims> claims = Optional.empty();
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            claims = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
        }
        if (claims.isEmpty()) {
            errorWriter.write(response, 401, "No autenticado", request.getRequestURI());
            return;
        }
        AuthenticatedUser principal =
                new AuthenticatedUser(claims.get().userId(), claims.get().email(), claims.get().role());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
        var token = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}