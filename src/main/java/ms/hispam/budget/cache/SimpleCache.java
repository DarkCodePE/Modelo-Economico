package ms.hispam.budget.cache;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleCache<K, V> {
    static final String TYPEMONTH="yyyyMM";
    private final int maxSize;
    private final LinkedHashMap<LocalDate, Double> cache;

    public SimpleCache(int maxSize) {
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<LocalDate, Double> eldest) {
                return size() > maxSize;
            }
        };
    }
}
