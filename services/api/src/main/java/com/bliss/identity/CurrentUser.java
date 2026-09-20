package com.bliss.identity;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.List;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public class CurrentUser {
    @Inject JsonWebToken token;
    @Inject SecurityIdentity identity;

    public String subject() {
        if (identity.isAnonymous() || token.getSubject() == null || token.getSubject().isBlank()) {
            throw new NotAuthorizedException("Bearer");
        }
        return token.getSubject();
    }

    public String initialName() {
        String name = token.getClaim("preferred_username");
        if (name == null || name.isBlank()) name = "Bliss member";
        return name.substring(0, Math.min(name.length(), 120));
    }

    public List<String> capabilities() {
        return List.of("customer", "provider", "admin").stream().filter(identity::hasRole).toList();
    }
}
