package com.infrastructure.security;

import com.domain.services.ITokenService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
public class JwtTokenServiceImpl implements ITokenService {
    private static final Logger logger = LoggerFactory.getLogger(JwtTokenServiceImpl.class);

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:86400000}") // Default: 24 hours
    private int jwtExpirationMs;

    private final Key signingKey;

    public JwtTokenServiceImpl(@Value("${app.jwt.secret:}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            logger.info("Generated secure random key for JWT signing");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
            if (keyBytes.length * 8 < 512) {
                logger.warn("Provided JWT secret is less than 512 bits. Generating a secure key instead.");
                this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
            } else {
                this.signingKey = Keys.hmacShaKeyFor(keyBytes);
                logger.info("Using provided JWT secret for signing");
            }
        }
    }

    @Override
    public String generateToken(String userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        String authorities = String.join(",", roles);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim("id", userId)
                .claim("roles", authorities)
                .signWith(signingKey, SignatureAlgorithm.HS512)
                .compact();
    }

    @Override
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }

        return false;
    }

    @Override
    public long getExpirationTime() {
        return jwtExpirationMs;
    }
}