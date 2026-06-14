package com.pascalito.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolTableTest {

    private static Symbol sym(String name, Type type) {
        return new Symbol(name, type, 1, 1);
    }

    // ===== Escopos aninhados =====

    @Test
    void childScopeReferencesParent() {
        SymbolTable global = new SymbolTable();
        SymbolTable child = new SymbolTable(global);
        assertSame(global, child.parent());
        assertNull(global.parent());
    }

    @Test
    void lookupWalksUpToParentScope() {
        SymbolTable global = new SymbolTable();
        global.declare(sym("a", Type.INTEGER));
        SymbolTable child = new SymbolTable(global);

        assertNotNull(child.lookup("a"), "lookup deve subir para o escopo pai");
        assertEquals(Type.INTEGER, child.lookup("a").type());
    }

    @Test
    void lookupLocalDoesNotSeeParent() {
        SymbolTable global = new SymbolTable();
        global.declare(sym("a", Type.INTEGER));
        SymbolTable child = new SymbolTable(global);

        assertNull(child.lookupLocal("a"), "lookupLocal vê apenas o escopo atual");
    }

    @Test
    void localDeclarationShadowsParent() {
        SymbolTable global = new SymbolTable();
        global.declare(sym("x", Type.INTEGER));
        SymbolTable child = new SymbolTable(global);

        assertTrue(child.declare(sym("x", Type.BOOLEAN)),
                "redeclarar em escopo filho é sombra, não erro");
        assertEquals(Type.BOOLEAN, child.lookup("x").type(), "filho deve sombrear o pai");
        assertEquals(Type.INTEGER, global.lookup("x").type(), "pai permanece inalterado");
    }

    @Test
    void redeclarationInSameScopeFails() {
        SymbolTable global = new SymbolTable();
        assertTrue(global.declare(sym("a", Type.INTEGER)));
        assertFalse(global.declare(sym("a", Type.BOOLEAN)),
                "redeclaração no mesmo escopo deve falhar");
    }

    // ===== Deslocamento (offset) =====

    @Test
    void offsetsAreAssignedSequentiallyByTypeSize() {
        SymbolTable t = new SymbolTable();
        t.declare(sym("a", Type.INTEGER)); // 2 bytes
        t.declare(sym("b", Type.BOOLEAN)); // 1 byte
        t.declare(sym("c", Type.INTEGER)); // 2 bytes

        assertEquals(0, t.lookup("a").offset());
        assertEquals(2, t.lookup("b").offset());
        assertEquals(3, t.lookup("c").offset());
    }

    @Test
    void eachScopeHasItsOwnFrameOffsets() {
        SymbolTable global = new SymbolTable();
        global.declare(sym("a", Type.INTEGER));
        SymbolTable child = new SymbolTable(global);
        child.declare(sym("local", Type.BOOLEAN));

        assertEquals(0, child.lookup("local").offset(),
                "cada frame começa o deslocamento do zero");
    }

    @Test
    void typeSizeInBytes() {
        assertEquals(2, Type.INTEGER.sizeInBytes());
        assertEquals(1, Type.BOOLEAN.sizeInBytes());
        assertEquals(2, Type.STRING.sizeInBytes());
    }
}
