package com.nordlab.hub.service;

import com.nordlab.hub.domain.UserAccount;
import com.nordlab.hub.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserAccountRepository users;
    public CurrentUserService(UserAccountRepository users){this.users=users;}
    public UUID id(){Authentication auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!(auth.getPrincipal() instanceof UUID id))throw new AccessDeniedException("Authentication required");return id;}
    public UserAccount require(){return users.findById(id()).orElseThrow(()->new AccessDeniedException("Account is no longer available"));}
}
