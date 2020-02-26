package io.github.whetfire.lateral;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    protected Map<Symbol, Object> symMap;

    Environment() {
        symMap = new HashMap<>();
    }

    Environment(int initialCapacity) {
        symMap = new HashMap<>(initialCapacity);
    }

    public void insert(Symbol symbol, Object obj) {
        symMap.put(symbol, obj);
    }

    public Object get(Symbol symbol) {
        return symMap.get(symbol);
    }

}
