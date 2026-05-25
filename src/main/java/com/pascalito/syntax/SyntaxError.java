package com.pascalito.syntax;

public record SyntaxError(int line, int column, String message) {

    @Override
    public String toString() {
        return "linha %d, coluna %d: %s".formatted(line, column, message);
    }
}
