package in.gov.aicte.platform.auth;

import in.gov.aicte.platform.auth.dto.DevUserDto;
import in.gov.aicte.platform.auth.dto.TokenRequest;
import in.gov.aicte.platform.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and active profile resolution")
public class AuthController {

    private final DevTokenService devTokenService;

    public AuthController(DevTokenService devTokenService) {
        this.devTokenService = devTokenService;
    }

    @PostMapping("/token")
    @Operation(summary = "Issue authentication bearer token for regular user portal", description = "Generates access token and user profile for authenticated user portal session")
    public ResponseEntity<TokenResponse> issueToken(@RequestBody(required = false) TokenRequest request) {
        String username = request != null ? request.username() : null;
        String role = request != null ? request.role() : null;
        String password = request != null ? request.password() : null;
        return ResponseEntity.ok(devTokenService.issueUserPortalToken(username, role, password));
    }

    @PostMapping("/admin/token")
    @Operation(summary = "Issue administrative authentication bearer token", description = "Generates access token and administrator profile for SAGE Admin Panel")
    public ResponseEntity<TokenResponse> issueAdminToken(@RequestBody(required = false) TokenRequest request) {
        String username = request != null ? request.username() : null;
        String password = request != null ? request.password() : null;
        return ResponseEntity.ok(devTokenService.issueAdminPortalToken(username, password));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated profile", description = "Returns active user profile from SecurityContext")
    public ResponseEntity<DevUserDto> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getDetails() instanceof DevUserDto userDto) {
                return ResponseEntity.ok(userDto);
            }
            if (auth.getPrincipal() instanceof String userId) {
                Optional<DevUserDto> userOpt = devTokenService.findUserById(userId);
                if (userOpt.isPresent()) {
                    return ResponseEntity.ok(userOpt.get());
                }
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
