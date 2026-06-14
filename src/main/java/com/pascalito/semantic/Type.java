package com.pascalito.semantic;

public enum Type {
    INTEGER, BOOLEAN, STRING, ERROR;

    public String displayName() {
        return name().toLowerCase();
    }

    /**
     * Tamanho em bytes ocupado no frame da pilha / seção {@code .data}.
     * {@code INTEGER}/{@code STRING} ocupam uma WORD (2 bytes, {@code dw});
     * {@code BOOLEAN} ocupa 1 byte ({@code db}). {@code ERROR} não reserva espaço.
     */
    public int sizeInBytes() {
        return switch (this) {
            case INTEGER, STRING -> 2;
            case BOOLEAN -> 1;
            case ERROR -> 0;
        };
    }
}
