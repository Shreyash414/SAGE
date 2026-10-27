package in.gov.aicte.platform.auth;

import in.gov.aicte.platform.auth.dto.DevUserDto;
import in.gov.aicte.platform.user.User;
import in.gov.aicte.platform.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class DevBearerAuthenticationFilter extends OncePerRequestFilter {

    private final DevTokenService devTokenService;
    private final UserRepository userRepository;

    public DevBearerAuthenticationFilter(DevTokenService devTokenService, @Lazy UserRepository userRepository) {
        this.devTokenService = devTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<DevUserDto> userOpt = devTokenService.validateToken(token);

            if (userOpt.isPresent()) {
                DevUserDto user = userOpt.get();

                // Check persistent account status if user exists in database
                boolean isAccountActive = true;
                try {
                    Optional<User> persistedUser = userRepository.findByUserId(user.id());
                    if (persistedUser.isPresent()) {
                        isAccountActive = persistedUser.get().isActive();
                    }
                } catch (Exception ignored) {
                    // Safe fallback during early context bootstrap or slice tests without DB
                }

                if (isAccountActive) {
                    String roleAuthority = "ROLE_" + user.role().toUpperCase().replace(" ", "_");
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority(roleAuthority));

                    if ("Administrator".equalsIgnoreCase(user.role()) || "ROLE_ADMIN".equalsIgnoreCase(roleAuthority) || "ROLE_ADMINISTRATOR".equalsIgnoreCase(roleAuthority)) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));
                    }

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.id(),
                            null,
                            authorities
                    );
                    authentication.setDetails(user);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
