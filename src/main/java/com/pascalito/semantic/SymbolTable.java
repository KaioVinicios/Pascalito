package com.pascalito.semantic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classe gerenciadora de tabela de símbolos baseada em hash, com suporte a
 * <b>escopos aninhados</b> por encadeamento de referências ao escopo pai e a
 * <b>deslocamento (offset)</b> em bytes por símbolo dentro do frame.
 *
 * <p>Cada instância representa um escopo: o escopo global tem {@code parent == null};
 * os blocos {@code BEGIN}/{@code END} abrem escopos filhos que apontam para o pai.
 * {@link #lookup(String)} sobe a cadeia (local → pai → … → global), enquanto
 * {@link #lookupLocal(String)} consulta apenas o escopo atual.</p>
 */
public class SymbolTable {

    public static final int ID_MAX_LENGTH = 16;

    private final SymbolTable parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    /** Deslocamento acumulado neste escopo; cada frame começa do zero. */
    private int frameSize = 0;

    /** Cria o escopo global (sem pai). */
    public SymbolTable() {
        this(null);
    }

    /** Cria um escopo filho encadeado ao {@code parent} informado. */
    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    /** Escopo pai, ou {@code null} se este for o escopo global. */
    public SymbolTable parent() {
        return parent;
    }

    public static String normalize(String rawId) {
        return rawId.length() > ID_MAX_LENGTH ? rawId.substring(0, ID_MAX_LENGTH) : rawId;
    }

    /**
     * Declara um símbolo <b>no escopo atual</b>, atribuindo-lhe o próximo deslocamento
     * disponível no frame e avançando o tamanho do frame pelo tamanho do tipo.
     *
     * @return {@code false} se o nome já existir <i>neste</i> escopo (redeclaração);
     *         redeclarar um nome herdado do pai é permitido (sombra de variável).
     */
    public boolean declare(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol.withOffset(frameSize));
        frameSize += symbol.type().sizeInBytes();
        return true;
    }

    /** Resolve um nome subindo a cadeia de escopos (local → pai → … → global). */
    public Symbol lookup(String name) {
        for (SymbolTable scope = this; scope != null; scope = scope.parent) {
            Symbol found = scope.symbols.get(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Resolve um nome consultando <b>apenas</b> o escopo atual. */
    public Symbol lookupLocal(String name) {
        return symbols.get(name);
    }

    public boolean contains(String name) {
        return lookup(name) != null;
    }

    /** Símbolos declarados neste escopo (não inclui os herdados do pai). */
    public Collection<Symbol> all() {
        return Collections.unmodifiableCollection(symbols.values());
    }

    public int size() {
        return symbols.size();
    }
}
