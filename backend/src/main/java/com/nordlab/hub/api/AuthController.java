package com.nordlab.hub.api;

import com.nordlab.hub.service.AuthService;
import com.nordlab.hub.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;private final CurrentUserService current;
    public AuthController(AuthService auth,CurrentUserService current){this.auth=auth;this.current=current;}
    @PostMapping("/register") @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public AuthService.Session register(@Valid @RequestBody RegisterRequest request){return auth.register(request.email(),request.password(),request.displayName(),request.workspaceName());}
    @PostMapping("/login") public AuthService.Session login(@Valid @RequestBody LoginRequest request){return auth.login(request.email(),request.password());}
    @GetMapping("/me") public MeResponse me(){UUID id=current.id();return new MeResponse(auth.user(id),auth.workspaces(id));}
    public record RegisterRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=12,max=100) String password,@NotBlank @Size(max=80) String displayName,@Size(max=80) String workspaceName){}
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=100) String password){}
    public record MeResponse(AuthService.UserView user,List<AuthService.WorkspaceSummary> workspaces){public MeResponse(com.nordlab.hub.domain.UserAccount u,List<AuthService.WorkspaceSummary>w){this(new AuthService.UserView(u.getId(),u.getEmail(),u.getDisplayName(),u.isGlobalAdmin()),w);}}
}
