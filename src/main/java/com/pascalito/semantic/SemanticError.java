package com.pascalito.semantic;

public record SemanticError(int line, int column, String message) {

    @Override
    public String toString() {
        return "linha %d, coluna %d: %s".formatted(line, column, message);
    }
}
