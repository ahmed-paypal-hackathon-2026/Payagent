package com.payagent.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository userRepository;
    private final boolean testProfile;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, UserDetailsServiceImpl userDetailsService,
                         UserRepository userRepository,
                         Environment environment) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.testProfile = environment.getActiveProfiles().length > 0 &&
            java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equalsIgnoreCase("test"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No Bearer token – clear stale authentication in production
            if (!testProfile) {
                SecurityContextHolder.clearContext();
            } else {
                // Test profile: propagate existing MockMvc authentication to request attributes
                Authentication existing = SecurityContextHolder.getContext().getAuthentication();
                if (existing != null && existing.isAuthenticated()) {
                    Object principal = existing.getPrincipal();
                    if (principal instanceof UUID uuid) {
                        request.setAttribute("userId", uuid);
                    } else if (principal instanceof String email) {
                        userRepository.findByEmail(email).ifPresent(user -> {
                            request.setAttribute("userId", user.getId());
                            request.setAttribute("userEmail", email);
                        });
                    }
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            // Validate token and extract claims; will throw on expired or invalid
            jwtTokenProvider.parseToken(token);
        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token expired\"}");
            return;
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        UUID userId = jwtTokenProvider.getUserId(token);
        String email = jwtTokenProvider.getEmail(token);

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // Use userId as principal for consistent identity contract; authorities from UserDetails
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(userId, null, userDetails.getAuthorities());

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Store userId in request attributes for easy access in controllers
        request.setAttribute("userId", userId);
        request.setAttribute("userEmail", email);

        filterChain.doFilter(request, response);
    }
}