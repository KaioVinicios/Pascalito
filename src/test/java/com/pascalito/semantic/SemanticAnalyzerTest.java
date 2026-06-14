package com.pascalito.semantic;

import com.pascalito.lex.LexicalErrorListener;
import com.pascalito.parser.PascalitoLexer;
import com.pascalito.parser.PascalitoParser;
import com.pascalito.syntax.SyntaxErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticAnalyzerTest {

    private SemanticAnalyzer analyze(String source) {
        PascalitoLexer lexer = new PascalitoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        PascalitoParser parser = new PascalitoParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        SyntaxErrorListener syn = new SyntaxErrorListener();
        parser.addErrorListener(syn);
        ParseTree tree = parser.prog();
        assertFalse(syn.hasErrors(),
                () -> "Test fixture should be syntactically valid: " + syn.getErrors());
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(tree);
        return analyzer;
    }

    private void assertNoErrors(String source) {
        SemanticAnalyzer a = analyze(source);
        assertFalse(a.hasErrors(),
                () -> "Expected no semantic errors, got: " + a.getErrors());
    }

    private List<SemanticError> errorsOf(String source) {
        SemanticAnalyzer a = analyze(source);
        assertTrue(a.hasErrors(), "Expected at least one semantic error");
        return a.getErrors();
    }

    // ===== Declarações e tabela de símbolos =====

    @Test
    void declaresAllVariablesInSymbolTable() {
        SemanticAnalyzer a = analyze("""
                program p;
                var a, b: integer; c: boolean; d: string;
                begin a := 0 end.
                """);
        assertFalse(a.hasErrors());
        assertEquals(4, a.getSymbolTable().size());
        assertEquals(Type.INTEGER, a.getSymbolTable().lookup("a").type());
        assertEquals(Type.INTEGER, a.getSymbolTable().lookup("b").type());
        assertEquals(Type.BOOLEAN, a.getSymbolTable().lookup("c").type());
        assertEquals(Type.STRING,  a.getSymbolTable().lookup("d").type());
    }

    @Test
    void redeclarationIsReported() {
        List<SemanticError> errs = errorsOf("""
                program p;
                var a: integer; a: boolean;
                begin a := 0 end.
                """);
        assertEquals(1, errs.stream().filter(e -> e.message().contains("já declarado")).count());
    }

    @Test
    void identifierIsTruncatedTo16CharsInSymbolTable() {
        SemanticAnalyzer a = analyze("""
                program p;
                var identificadorMuitoGrande: integer;
                begin identificadorMui := 1 end.
                """);
        assertFalse(a.hasErrors());
        assertNotNull(a.getSymbolTable().lookup("identificadorMui"));
    }

    @Test
    void twoLongIdentifiersWithSamePrefixCollide() {
        // 'identificadorMuiAAA' e 'identificadorMuiBBB' truncam para 'identificadorMui' — colisão por spec
        List<SemanticError> errs = errorsOf("""
                program p;
                var identificadorMuiAAA: integer; identificadorMuiBBB: boolean;
                begin identificadorMui := 0 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("já declarado")));
    }

    @Test
    void assignsSequentialOffsetsByType() {
        SemanticAnalyzer a = analyze("""
                program p;
                var a: integer; b: boolean; c: integer;
                begin a := 0 end.
                """);
        assertFalse(a.hasErrors());
        assertEquals(0, a.getSymbolTable().lookup("a").offset()); // integer @ 0
        assertEquals(2, a.getSymbolTable().lookup("b").offset()); // boolean @ 2
        assertEquals(3, a.getSymbolTable().lookup("c").offset()); // integer @ 3
    }

    @Test
    void nestedBlocksResolveOuterScopeNames() {
        // blocos BEGIN/END aninhados abrem escopos filhos; o nome 'n' do escopo
        // do programa deve ser resolvido subindo a cadeia de escopos.
        assertNoErrors("""
                program p; var n: integer;
                begin
                  begin n := 1 end;
                  begin begin n := n + 1 end end
                end.
                """);
    }

    @Test
    void symbolTableExposedIsTheGlobalScope() {
        SemanticAnalyzer a = analyze("""
                program p; var n: integer;
                begin begin n := 1 end end.
                """);
        // após visitar, o escopo exposto é o global (raiz, sem pai) com as declarações
        assertNull(a.getSymbolTable().parent());
        assertEquals(1, a.getSymbolTable().size());
        assertNotNull(a.getSymbolTable().lookup("n"));
    }

    // ===== Overflow de constante inteira (CTE) — erro semântico V2 =====

    @Test
    void positiveCteOverflowIsSemanticError() {
        List<SemanticError> errs = errorsOf("""
                program p; var n: integer; begin n := 40000 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("Overflow de Constante")));
    }

    @Test
    void cteAtUpperBoundIsAccepted() {
        assertNoErrors("program p; var n: integer; begin n := 32767 end.");
    }

    @Test
    void cteJustAboveUpperBoundIsError() {
        List<SemanticError> errs = errorsOf("program p; var n: integer; begin n := 32768 end.");
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("Overflow de Constante")));
    }

    @Test
    void negativeCteAtLowerBoundIsAccepted() {
        // -32768 é válido pela faixa COM sinal, embora a magnitude 32768 sozinha estoure
        assertNoErrors("program p; var n: integer; begin n := -32768 end.");
    }

    @Test
    void negativeCteBelowLowerBoundIsError() {
        List<SemanticError> errs = errorsOf("program p; var n: integer; begin n := -32769 end.");
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("Overflow de Constante")));
    }

    // ===== Truncamento de identificador longo — aviso (não erro) =====

    @Test
    void longIdentifierEmitsWarningNotError() {
        SemanticAnalyzer a = analyze("""
                program p; var identificadorMuitoGrande: integer;
                begin identificadorMui := 1 end.
                """);
        assertFalse(a.hasErrors(), () -> "truncamento é aviso, não erro: " + a.getErrors());
        assertTrue(a.getWarnings().stream().anyMatch(w -> w.contains("identificadorMuitoGrande")),
                () -> "esperava aviso de truncamento, avisos: " + a.getWarnings());
    }

    @Test
    void shortIdentifierEmitsNoWarning() {
        SemanticAnalyzer a = analyze("program p; var n: integer; begin n := 1 end.");
        assertTrue(a.getWarnings().isEmpty());
    }

    // ===== Uso de variável não declarada =====

    @Test
    void undeclaredVariableInExpressionReported() {
        List<SemanticError> errs = errorsOf("""
                program p; var x: integer; begin x := y + 1 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("'y' não declarada")));
    }

    @Test
    void undeclaredVariableInReadReported() {
        List<SemanticError> errs = errorsOf("""
                program p; var x: integer; begin read(x, z) end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("'z' não declarada")));
    }

    @Test
    void undeclaredVariableOnLeftOfAssignment() {
        List<SemanticError> errs = errorsOf("""
                program p; var x: integer; begin q := 1 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("'q' não declarada")));
    }

    // ===== Compatibilidade de tipos em atribuição =====

    @Test
    void assigningBooleanToIntegerIsError() {
        assertFalse(errorsOf("""
                program p; var n: integer; begin n := true end.
                """).isEmpty());
    }

    @Test
    void assigningIntegerToBooleanIsError() {
        assertFalse(errorsOf("""
                program p; var b: boolean; begin b := 1 end.
                """).isEmpty());
    }

    @Test
    void assigningIntegerToIntegerIsFine() {
        assertNoErrors("program p; var n: integer; begin n := 42 end.");
    }

    @Test
    void assigningBooleanToBooleanIsFine() {
        assertNoErrors("program p; var b: boolean; begin b := true and false end.");
    }

    // ===== Operadores aritméticos =====

    @Test
    void arithmeticRequiresIntegers() {
        List<SemanticError> errs = errorsOf("""
                program p; var n: integer; b: boolean; begin n := b + 1 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("integer")));
    }

    @Test
    void unaryMinusOnIntegerIsFine() {
        assertNoErrors("program p; var n: integer; begin n := -5 end.");
    }

    @Test
    void unaryMinusOnBooleanIsError() {
        assertFalse(errorsOf("""
                program p; var n: integer; b: boolean; begin n := -b end.
                """).isEmpty());
    }

    // ===== Operadores lógicos =====

    @Test
    void logicalOperatorsRequireBoolean() {
        List<SemanticError> errs = errorsOf("""
                program p; var n: integer; b: boolean; begin b := n and true end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("and")));
    }

    @Test
    void unaryNegOnIntegerIsError() {
        assertFalse(errorsOf("""
                program p; var n: integer; begin n := ~5 end.
                """).isEmpty());
    }

    @Test
    void unaryNegOnBooleanIsFine() {
        assertNoErrors("program p; var b: boolean; begin b := ~true end.");
    }

    // ===== Operadores relacionais =====

    @Test
    void relationalOnIntegersReturnsBoolean() {
        assertNoErrors("program p; var n: integer; b: boolean; begin b := n > 0 end.");
    }

    @Test
    void relationalOnMixedTypesIsError() {
        assertFalse(errorsOf("""
                program p; var n: integer; b: boolean; begin b := n > true end.
                """).isEmpty());
    }

    // ===== Condições de if/while =====

    @Test
    void ifConditionMustBeBoolean() {
        List<SemanticError> errs = errorsOf("""
                program p; var n: integer; begin if n then n := 1 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("'if'")));
    }

    @Test
    void whileConditionMustBeBoolean() {
        List<SemanticError> errs = errorsOf("""
                program p; var n: integer; begin while n + 1 do n := 0 end.
                """);
        assertTrue(errs.stream().anyMatch(e -> e.message().contains("'while'")));
    }

    @Test
    void booleanConditionsAreAccepted() {
        assertNoErrors("""
                program p; var n: integer;
                begin
                  if n > 0 then n := 0;
                  while n < 10 do n := n + 1
                end.
                """);
    }

    // ===== read/write =====

    @Test
    void writeAcceptsMixedExpressionsAndCadeia() {
        assertNoErrors("""
                program p; var n: integer; b: boolean;
                begin write("oi", n, b, n + 1) end.
                """);
    }

    // ===== Propagação de erro =====

    @Test
    void errorTypePropagatesWithoutCascadingMessages() {
        // 'q' não existe — gera 1 erro. Não deve haver "cadeia" de erros derivados.
        List<SemanticError> errs = errorsOf("""
                program p; var n: integer; begin n := q + 1 + 2 end.
                """);
        long undeclared = errs.stream().filter(e -> e.message().contains("'q' não declarada")).count();
        assertEquals(1, undeclared);
        // O erro de "Atribuição incompatível" não deve aparecer porque o tipo virou ERROR
        assertTrue(errs.stream().noneMatch(e -> e.message().contains("Atribuição incompatível")),
                "ERROR deve curto-circuitar, não cascatear");
    }
}
