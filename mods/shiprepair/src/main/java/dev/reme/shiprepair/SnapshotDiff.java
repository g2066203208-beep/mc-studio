package dev.reme.shiprepair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Two phases: put back original blocks, THEN remove extras. */
public final class SnapshotDiff {
    private SnapshotDiff() {}
    public record Edit<T>(long offset,T before,T after) {}
    public static <T> List<Edit<T>> plan(Map<Long,T> saved,Map<Long,T> current) {
        var changes=new ArrayList<Edit<T>>();
        saved.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(e->!Objects.equals(e.getValue(),current.get(e.getKey())))
                .forEach(e->changes.add(new Edit<>(e.getKey(),current.get(e.getKey()),e.getValue())));
        current.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(e->!saved.containsKey(e.getKey()))
                .forEach(e->changes.add(new Edit<>(e.getKey(),e.getValue(),null)));
        return List.copyOf(changes);
    }
}
