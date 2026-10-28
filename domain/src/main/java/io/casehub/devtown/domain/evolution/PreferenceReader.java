package io.casehub.devtown.domain.evolution;

import java.util.OptionalDouble;

@FunctionalInterface
public interface PreferenceReader {
    OptionalDouble read(String scope, String namespace, String key);
}
