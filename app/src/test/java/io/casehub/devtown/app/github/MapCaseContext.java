package io.casehub.devtown.app.github;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.context.CaseContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

class MapCaseContext implements CaseContext {

    private final Map<String, Object> data;

    MapCaseContext(Map<String, Object> data) {
        this.data = data;
    }

    @Override public Map<String, Object> getData() { return data; }
    @Override public Object get(String key) { return data.get(key); }
    @Override public boolean contains(String key) { return data.containsKey(key); }

    @Override
    @SuppressWarnings("unchecked")
    public Object getPath(String path) {
        String[] parts = path.split("\\.", 2);
        Object val = data.get(parts[0]);
        if (parts.length == 1 || val == null) return val;
        if (val instanceof Map<?, ?> m) return new MapCaseContext((Map<String, Object>) m).getPath(parts[1]);
        return null;
    }

    @Override
    public String getPathAsString(String path) {
        Object val = getPath(path);
        return val != null ? val.toString() : null;
    }

    @Override public CaseContext set(String k, Object v) { throw new UnsupportedOperationException(); }
    @Override public <T> T getAs(String k, Class<T> t) { throw new UnsupportedOperationException(); }
    @Override public <T> T getOrDefault(String k, T d) { throw new UnsupportedOperationException(); }
    @Override public Object computeIfAbsent(String k, Function<String, Object> f) { throw new UnsupportedOperationException(); }
    @Override public Object putIfAbsent(String k, Object v) { throw new UnsupportedOperationException(); }
    @Override public boolean compareAndSet(String k, Object e, Object n) { throw new UnsupportedOperationException(); }
    @Override public CaseContext update(String k, Function<Object, Object> f) { throw new UnsupportedOperationException(); }
    @Override public String getString(String k) { throw new UnsupportedOperationException(); }
    @Override public Integer getInt(String k) { throw new UnsupportedOperationException(); }
    @Override public Long getLong(String k) { throw new UnsupportedOperationException(); }
    @Override public Double getDouble(String k) { throw new UnsupportedOperationException(); }
    @Override public Boolean getBoolean(String k) { throw new UnsupportedOperationException(); }
    @Override public <T> List<T> getList(String k, Class<T> t) { throw new UnsupportedOperationException(); }
    @Override public CaseContext setPath(String p, Object v) { throw new UnsupportedOperationException(); }
    @Override public Optional<JsonNode> applyAndDiff(String p, Object v) { throw new UnsupportedOperationException(); }
    @Override public CaseContext setAll(Map<String, Object> v) { throw new UnsupportedOperationException(); }
    @Override public Map<String, Object> getAll(String... k) { throw new UnsupportedOperationException(); }
    @Override public CaseContext remove(String k) { throw new UnsupportedOperationException(); }
    @Override public CaseContext clear() { throw new UnsupportedOperationException(); }
    @Override public Set<String> getKeys() { throw new UnsupportedOperationException(); }
    @Override public boolean isEmpty() { throw new UnsupportedOperationException(); }
    @Override public int size() { throw new UnsupportedOperationException(); }
    @Override public JsonNode asJsonNode() { throw new UnsupportedOperationException(); }
    @Override public CaseContext merge(CaseContext o) { throw new UnsupportedOperationException(); }
    @Override public CaseContext snapshot() { throw new UnsupportedOperationException(); }
    @Override public JsonNode diff(CaseContext o) { throw new UnsupportedOperationException(); }
    @Override public void applyDiff(JsonNode d) { throw new UnsupportedOperationException(); }
    @Override public long getVersion() { throw new UnsupportedOperationException(); }
    @Override public io.casehub.api.context.ReadableLayer layer(String n) { throw new UnsupportedOperationException(); }
}
