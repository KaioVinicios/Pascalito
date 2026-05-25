package com.pascalito.lex;

public record TokenInfo(String tipo, String atributo, int line, int column) {

    public boolean hasAtributo() {
        return atributo != null;
    }
}
