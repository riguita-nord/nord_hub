package com.nordlab.hub.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String ISSUER="nord-hub";
    private final byte[] secret;
    private final Duration ttl;
    private final ObjectMapper mapper;

    public JwtService(@Value("${nordhub.security.jwt-secret}") String secret,
                      @Value("${nordhub.security.token-ttl:PT30M}") Duration ttl,
                      ObjectMapper mapper) {
        this.secret=secret.getBytes(StandardCharsets.UTF_8);
        if(this.secret.length<32) throw new IllegalStateException("NORD_HUB_JWT_SECRET must contain at least 32 bytes");
        this.ttl=ttl; this.mapper=mapper;
    }

    public String issue(String subject) {
        try {
            String header=encode(mapper.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));
            String payload=encode(mapper.writeValueAsBytes(Map.of("iss",ISSUER,"sub",subject,"iat",Instant.now().getEpochSecond(),"exp",Instant.now().plus(ttl).getEpochSecond())));
            String content=header+"."+payload;
            return content+"."+encode(sign(content));
        } catch(Exception e) { throw new IllegalStateException("Could not create access token",e); }
    }

    public Optional<String> subject(String token) {
        try {
            String[] parts=token.split("\\.");
            if(parts.length!=3) return Optional.empty();
            byte[] expected=sign(parts[0]+"."+parts[1]);
            byte[] actual=Base64.getUrlDecoder().decode(parts[2]);
            if(!MessageDigest.isEqual(expected,actual)) return Optional.empty();
            Map<String,Object> claims=mapper.readValue(Base64.getUrlDecoder().decode(parts[1]),new TypeReference<>(){});
            long exp=((Number)claims.get("exp")).longValue();
            if(!ISSUER.equals(claims.get("iss"))||Instant.now().getEpochSecond()>=exp) return Optional.empty();
            return Optional.ofNullable((String)claims.get("sub"));
        } catch(Exception e) { return Optional.empty(); }
    }

    public Duration ttl(){return ttl;}
    private byte[] sign(String value) throws Exception {Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));}
    private static String encode(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
}
