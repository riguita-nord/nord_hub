package com.nordlab.hub.api;

import com.nordlab.hub.repository.UserAccountRepository;
import com.nordlab.hub.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {
    private final UserAccountRepository users;
    private final AuthService auth;
    private final byte[] setupToken;

    public SetupController(UserAccountRepository users, AuthService auth,
            @Value("${nordhub.security.setup-token:}") String setupToken) {
        this.users=users; this.auth=auth; this.setupToken=setupToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/status")
    public Map<String,Boolean> status() {
        return Map.of("available",setupToken.length>=32 && !users.existsByGlobalAdminTrue());
    }

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public synchronized AuthService.Session bootstrap(@Valid @RequestBody BootstrapRequest request) {
        if(setupToken.length<32 || users.existsByGlobalAdminTrue())
            throw new ResponseStatusException(HttpStatus.GONE,"Initial setup is already complete or disabled");
        byte[] supplied=request.setupToken().getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(setupToken,supplied))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Setup token is invalid");
        AuthService.Session session=auth.register(request.email(),request.password(),request.displayName(),request.workspaceName());
        if(!session.user().globalAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Use the administrator email configured by the installer");
        return session;
    }

    public record BootstrapRequest(@NotBlank @Size(min=32,max=256) String setupToken,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=12,max=100) String password,
            @NotBlank @Size(max=80) String displayName,
            @NotBlank @Size(max=80) String workspaceName) {}
}
