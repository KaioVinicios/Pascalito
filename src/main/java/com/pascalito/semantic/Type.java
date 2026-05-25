package com.pascalito.semantic;

public enum Type {
    INTEGER, BOOLEAN, STRING, ERROR;

    public String displayName() {
        return name().toLowerCase();
    }
}
