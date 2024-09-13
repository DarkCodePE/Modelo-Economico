package ms.hispam.budget.cache;

import ms.hispam.budget.entity.mysql.NominaPaymentComponentLink;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class NominaPaymentComponentLinksCache {
    private static final NominaPaymentComponentLinksCache INSTANCE = new NominaPaymentComponentLinksCache();
    private final ConcurrentMap<String, ConcurrentMap<String, List<NominaPaymentComponentLink>>> cache = new ConcurrentHashMap<>();

    private NominaPaymentComponentLinksCache() {}

    public static NominaPaymentComponentLinksCache getInstance() {
        return INSTANCE;
    }

    public ConcurrentMap<String, List<NominaPaymentComponentLink>> getLinks(String bu, Supplier<List<NominaPaymentComponentLink>> supplier) {
        return cache.computeIfAbsent(bu, k -> supplier.get().stream()
                .collect(Collectors.groupingByConcurrent(n -> n.getNominaConcept().getCodeNomina())));
    }
}