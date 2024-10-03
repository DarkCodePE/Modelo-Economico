package ms.hispam.budget.cache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ms.hispam.budget.dto.ProjectionSecondDTO;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ProjectionCache {

    private final Cache<String, ProjectionSecondDTO> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(3)
            .build();

    private final Cache<String, String> hashCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(3)
            .build();

    public void put(String key, ProjectionSecondDTO projection, String hash) {
        cache.put(key, projection);
        hashCache.put(key, hash);
    }

    public ProjectionSecondDTO get(String key) {
        return cache.getIfPresent(key);
    }

    public String getHash(String key) {
        return hashCache.getIfPresent(key);
    }

    public boolean containsKey(String key) {
        return cache.getIfPresent(key) != null;
    }

    public void remove(String key) {
        cache.invalidate(key);
        hashCache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();
        hashCache.invalidateAll();
    }
}