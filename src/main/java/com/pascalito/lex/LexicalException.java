package com.pascalito.lex;

public class LexicalException extends RuntimeException {

    private final int line;
    private final int column;

    public LexicalException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
