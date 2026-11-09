package ru.mephi.booking.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ru.mephi.booking.domain.AppUser;
import ru.mephi.shared.JwtSupport;

@Service
public class TokenService {
    private final JwtEncoder userEncoder;
    private final JwtEncoder internalEncoder;
    public TokenService(@Value("${app.user-public-key}") Resource userPublic,
    @Value("${app.user-private-key}") Resource userPrivate,
    @Value("${app.internal-public-key}") Resource internalPublic,
    @Value("${app.internal-private-key}") Resource internalPrivate) throws Exception {
        userEncoder = encoder(userPublic, userPrivate, "user-demo");
        internalEncoder = encoder(internalPublic, internalPrivate, "internal-demo");
    }
    private JwtEncoder encoder(Resource pub, Resource priv, String id) throws Exception {
        var key = new RSAKey.Builder(JwtSupport.publicKey(pub)).privateKey(JwtSupport.privateKey(priv)).keyID(id).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
    public String user(AppUser user) {
        return encode(userEncoder, "hotel-booking", "hotel-api", user.getId().toString(), user.getRole().name(), 3600);
    }
    public String internal() {
        return encode(internalEncoder, "booking-internal", "hotel-internal", "booking-service", "INTERNAL", 60);
    }
    private String encode(JwtEncoder encoder, String issuer, String audience, String subject, String role, long ttl) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(subject)
        .issuedAt(now).expiresAt(now.plusSeconds(ttl)).id(UUID.randomUUID().toString()).claim("roles", List.of(role)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
