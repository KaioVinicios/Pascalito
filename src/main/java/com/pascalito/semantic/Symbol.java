package com.pascalito.semantic;

/**
 * Entrada da tabela de símbolos.
 *
 * @param name   identificador (lexema já truncado/normalizado)
 * @param type   tipo estático ({@code INTEGER}, {@code BOOLEAN} ou {@code STRING})
 * @param line   linha da declaração
 * @param column coluna da declaração
 * @param offset deslocamento em bytes no frame da pilha; {@code -1} enquanto não atribuído
 */
public record Symbol(String name, Type type, int line, int column, int offset) {

    /** Cria um símbolo ainda sem deslocamento (atribuído pela {@link SymbolTable} na declaração). */
    public Symbol(String name, Type type, int line, int column) {
        this(name, type, line, column, -1);
    }

    /** Retorna uma cópia deste símbolo com o deslocamento informado. */
    public Symbol withOffset(int offset) {
        return new Symbol(name, type, line, column, offset);
    }
}
