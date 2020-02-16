package io.github.whetfire.lateral;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private Map<Symbol, Object> symMap;

    Environment() {
        symMap = new HashMap<>(256);
    }

    public void insert(Symbol symbol, Object obj) {
        symMap.put(symbol, obj);
    }

    public Object get(Symbol symbol) {
        return symMap.get(symbol);
    }

}
