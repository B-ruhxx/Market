package com.tienda.ecommerce.auth;

import com.tienda.ecommerce.config.JwtProperties;
import com.tienda.ecommerce.usuario.Rol;
import com.tienda.ecommerce.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @jakarta.annotation.PostConstruct
    public void validarConfiguracion() {
        obtenerLlaveFirma();
    }

    private SecretKey obtenerLlaveFirma() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) no puede estar vacío");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) debe tener al menos 32 bytes (256 bits) para HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("id", usuario.getId())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(obtenerLlaveFirma())
                .compact();
    }

    public boolean esTokenValido(String token) {
        try {
            Claims claims = extraerClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Date extraerIssuedAt(String token) {
        return extraerClaims(token).getIssuedAt();
    }

    public Date extraerExpiration(String token) {
        return extraerClaims(token).getExpiration();
    }

    public String extraerEmail(String token) {
        return extraerClaims(token).getSubject();
    }

    public Long extraerId(String token) {
        Object idObj = extraerClaims(token).get("id");
        return idObj != null ? ((Number) idObj).longValue() : null;
    }

    public Rol extraerRol(String token) {
        String rolStr = (String) extraerClaims(token).get("rol");
        return rolStr != null ? Rol.valueOf(rolStr) : null;
    }

    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(obtenerLlaveFirma())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
