package com.nordlab.hub.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nordlab.hub.service.CurrentUserService;
import java.time.Instant;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Read-only, server-side connector for the verified Nord SaaS Lite APIs. */
@RestController
public class NordSaasIntegrationController {
    private final CurrentUserService current;
    private final RestClient nordSaas;
    private final boolean configured;

    public NordSaasIntegrationController(CurrentUserService current, RestClient.Builder builder,
            @Value("${nordhub.nord-saas.base-url:}") String baseUrl,
            @Value("${nordhub.nord-saas.session-token:}") String sessionToken) {
        this.current=current;
        String endpoint=validatedBaseUrl(baseUrl);
        this.configured=baseUrl!=null&&!baseUrl.isBlank()&&sessionToken!=null&&!sessionToken.isBlank();
        this.nordSaas=builder.baseUrl(endpoint)
                .defaultHeader("X-Nord-Session",sessionToken)
                .defaultHeader("Accept","application/json").build();
    }

    private String validatedBaseUrl(String value) {
        if(value==null||value.isBlank())return "http://localhost";
        URI uri=URI.create(value.trim());String host=uri.getHost();
        boolean local="localhost".equalsIgnoreCase(host)||"127.0.0.1".equals(host)||"::1".equals(host);
        if(host==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
                ||(uri.getPath()!=null&&!uri.getPath().isBlank()&&!"/".equals(uri.getPath()))
                ||(!"https".equalsIgnoreCase(uri.getScheme())&&!local))
            throw new IllegalArgumentException("Nord SaaS base URL must be an HTTPS origin (HTTP is allowed only for localhost)");
        return value.trim().replaceAll("/+$","");
    }

    @GetMapping("/api/v1/integrations/nord-saas/snapshot")
    public Map<String,Object> snapshot() {
        if(!current.require().isGlobalAdmin())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Global administrator access is required");
        if(!configured)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure the Nord SaaS URL and session token on the server");
        try {
            JsonNode platform=nordSaas.get().uri("/api/v1/status").retrieve().body(JsonNode.class);
            JsonNode readiness=nordSaas.get().uri("/health/ready").retrieve().body(JsonNode.class);
            JsonNode projects=nordSaas.get().uri("/api/v1/forge/projects").retrieve().body(JsonNode.class);
            JsonNode releases=nordSaas.get().uri("/api/v1/forge/releases").retrieve().body(JsonNode.class);
            return Map.of("platform",platform==null?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode():platform,
                    "readiness",readiness==null?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode():readiness,
                    "projects",projects==null?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode():projects,
                    "releases",releases==null?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode():releases,
                    "syncedAt",Instant.now().toString());
        } catch(ResponseStatusException ex) { throw ex; }
        catch(Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Nord SaaS could not provide the configured read-only snapshot");
        }
    }
}
