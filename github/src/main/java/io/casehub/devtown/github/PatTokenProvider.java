package io.casehub.devtown.github;

public class PatTokenProvider implements GitHubTokenProvider {

    private final String token;

    public PatTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return token;
    }
}
