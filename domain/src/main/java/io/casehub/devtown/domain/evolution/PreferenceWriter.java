package io.casehub.devtown.domain.evolution;

@FunctionalInterface
public interface PreferenceWriter {
  void write(String scope, String namespace, String key, String value);
}
