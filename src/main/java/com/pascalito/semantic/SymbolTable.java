package com.pascalito.semantic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SymbolTable {

    public static final int ID_MAX_LENGTH = 16;

    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public static String normalize(String rawId) {
        return rawId.length() > ID_MAX_LENGTH ? rawId.substring(0, ID_MAX_LENGTH) : rawId;
    }

    public boolean declare(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    public Symbol lookup(String name) {
        return symbols.get(name);
    }

    public boolean contains(String name) {
        return symbols.containsKey(name);
    }

    public Collection<Symbol> all() {
        return Collections.unmodifiableCollection(symbols.values());
    }

    public int size() {
        return symbols.size();
    }
}
