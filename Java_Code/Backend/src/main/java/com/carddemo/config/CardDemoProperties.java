package com.carddemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalised configuration (NFR-007). Nothing here is a business value; the whole application
 * dataset lives in PostgreSQL.
 */
@ConfigurationProperties(prefix = "carddemo")
public class CardDemoProperties {

    private final Jwt jwt = new Jwt();
    private final Migration migration = new Migration();
    private final Security security = new Security();
    private List<String> allowedOrigins = List.of("http://localhost:4200");

    public Jwt getJwt() {
        return jwt;
    }

    public Migration getMigration() {
        return migration;
    }

    public Security getSecurity() {
        return security;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public static class Jwt {

        /** HMAC signing secret; must be supplied by configuration or environment in any real deployment. */
        private String secret = "";
        /** Access token lifetime in minutes. */
        private long expirationMinutes = 60;
        private String issuer = "carddemo";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Migration {

        /** Run the COBOL fixture migration on startup when the database is still empty. */
        private boolean enabled = true;
        /** Force a reload even when data already exists. */
        private boolean force = false;
        /**
         * Optional filesystem directory holding the original {@code Cobol_Code} data files. When it
         * is absent, the copies bundled under {@code classpath:cobol-data} are used.
         */
        private String sourceDirectory = "";
        /** Password applied to every migrated legacy user; they must change it at first sign-in. */
        private String legacyPassword = "PASSWORD";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

        public String getSourceDirectory() {
            return sourceDirectory;
        }

        public void setSourceDirectory(String sourceDirectory) {
            this.sourceDirectory = sourceDirectory;
        }

        public String getLegacyPassword() {
            return legacyPassword;
        }

        public void setLegacyPassword(String legacyPassword) {
            this.legacyPassword = legacyPassword;
        }
    }

    public static class Security {

        /** Failed sign-in attempts before the account is temporarily locked. */
        private int maxFailedAttempts = 5;
        /** Lock duration in minutes after {@code maxFailedAttempts} consecutive failures. */
        private int lockMinutes = 15;
        /** Minimum length accepted for a new password. */
        private int minPasswordLength = 8;
        /** Role assigned to a self-service signup. */
        private String signupRole = "U";
        /** Whether the public signup endpoint is available. */
        private boolean signupEnabled = true;

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }

        public int getMinPasswordLength() {
            return minPasswordLength;
        }

        public void setMinPasswordLength(int minPasswordLength) {
            this.minPasswordLength = minPasswordLength;
        }

        public String getSignupRole() {
            return signupRole;
        }

        public void setSignupRole(String signupRole) {
            this.signupRole = signupRole;
        }

        public boolean isSignupEnabled() {
            return signupEnabled;
        }

        public void setSignupEnabled(boolean signupEnabled) {
            this.signupEnabled = signupEnabled;
        }
    }
}
