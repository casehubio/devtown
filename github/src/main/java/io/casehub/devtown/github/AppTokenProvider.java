package io.casehub.devtown.github;

import org.jboss.logging.Logger;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;

public class AppTokenProvider implements GitHubTokenProvider {

    private static final Logger LOG = Logger.getLogger(AppTokenProvider.class);

    private final String appId;
    private final String installationId;
    private final RSAPrivateKey privateKey;
    private final InstallationTokenClient tokenClient;
    private volatile CachedToken cached;

    public AppTokenProvider(String appId, String privateKeyPath,
                            String installationId,
                            InstallationTokenClient tokenClient) {
        this.appId = appId;
        this.installationId = installationId;
        this.privateKey = GitHubAppJwtGenerator.loadPrivateKey(privateKeyPath);
        this.tokenClient = tokenClient;
    }

    @Override
    public String getToken() {
        CachedToken current = cached;
        if (current != null && !current.isExpiringSoon(Instant.now())) {
            return current.token();
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        CachedToken current = cached;
        if (current != null && !current.isExpiringSoon(Instant.now())) {
            return current.token();
        }

        LOG.infof("Refreshing GitHub installation token for installation %s",
            installationId);
        String jwt = GitHubAppJwtGenerator.generateJwt(appId, privateKey);
        InstallationToken response = tokenClient.createInstallationToken(
            installationId, "Bearer " + jwt);
        cached = new CachedToken(response.token(), response.expiresAt());
        return response.token();
    }
}
