package com.pascalito.lex;

import com.pascalito.parser.PascalitoLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenPrinterTest {

    private List<TokenInfo> tokenize(String source) {
        PascalitoLexer lexer = new PascalitoLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(new LexicalErrorListener());
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        TokenPrinter printer = new TokenPrinter(lexer.getVocabulary());
        return printer.categorize(stream.getTokens());
    }

    @Test
    void mapsArithmeticOperators() {
        List<TokenInfo> infos = tokenize("+ - * /");
        assertEquals(4, infos.size());
        assertEquals(new TokenInfo("OPAD",   "MAIS",  1, 1), infos.get(0));
        assertEquals(new TokenInfo("OPAD",   "MENOS", 1, 3), infos.get(1));
        assertEquals(new TokenInfo("OPMULT", "VEZES", 1, 5), infos.get(2));
        assertEquals(new TokenInfo("OPMULT", "DIV",   1, 7), infos.get(3));
    }

    @Test
    void mapsLogicalOperators() {
        List<TokenInfo> infos = tokenize("or and ~");
        assertEquals(new TokenInfo("OPLOG", "OR",  1, 1), infos.get(0));
        assertEquals(new TokenInfo("OPLOG", "AND", 1, 4), infos.get(1));
        assertEquals(new TokenInfo("OPNEG", "NEG", 1, 8), infos.get(2));
    }

    @Test
    void mapsRelationalOperators() {
        List<TokenInfo> infos = tokenize("< <= > >= == <>");
        assertEquals("OPREL", infos.get(0).tipo());
        assertEquals("MENOR", infos.get(0).atributo());
        assertEquals("MENIG", infos.get(1).atributo());
        assertEquals("MAIOR", infos.get(2).atributo());
        assertEquals("MAIG",  infos.get(3).atributo());
        assertEquals("IGUAL", infos.get(4).atributo());
        assertEquals("DIFER", infos.get(5).atributo());
    }

    @Test
    void reservedWordsHaveNoAtributo() {
        List<TokenInfo> infos = tokenize("program var begin end integer boolean string if then else while do read write true false");
        for (TokenInfo info : infos) {
            assertNull(info.atributo(), "reserved word should have no ATRIBUTO: " + info.tipo());
        }
        assertEquals("PROGRAM", infos.get(0).tipo());
        assertEquals("FALSE",   infos.get(infos.size() - 1).tipo());
    }

    @Test
    void symbolsHaveNoAtributo() {
        List<TokenInfo> infos = tokenize("; : , . ( ) :=");
        List<String> expected = List.of("PVIG", "DPONTOS", "VIG", "PONTO", "ABPAR", "FPAR", "ATRIB");
        assertEquals(expected.size(), infos.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), infos.get(i).tipo());
            assertNull(infos.get(i).atributo());
        }
    }

    @Test
    void caseInsensitiveKeywordsRecognized() {
        List<TokenInfo> infos = tokenize("PROGRAM Begin END");
        assertEquals("PROGRAM", infos.get(0).tipo());
        assertEquals("BEGIN",   infos.get(1).tipo());
        assertEquals("END",     infos.get(2).tipo());
    }

    @Test
    void identifierShorterThanLimitIsPreserved() {
        List<TokenInfo> infos = tokenize("contador");
        assertEquals(new TokenInfo("ID", "contador", 1, 1), infos.get(0));
    }

    @Test
    void identifierLongerThan16CharsIsTruncated() {
        List<TokenInfo> infos = tokenize("identificadorMuitoGrande");
        assertEquals("ID", infos.get(0).tipo());
        assertEquals("identificadorMui", infos.get(0).atributo());
        assertEquals(16, infos.get(0).atributo().length());
    }

    @Test
    void cteWithinRangeIsAccepted() {
        List<TokenInfo> infos = tokenize("0 1 32767");
        assertEquals("0",     infos.get(0).atributo());
        assertEquals("1",     infos.get(1).atributo());
        assertEquals("32767", infos.get(2).atributo());
    }

    @Test
    void cteAboveLimitThrowsLexicalException() {
        LexicalException ex = assertThrows(LexicalException.class, () -> tokenize("32768"));
        assertEquals(1, ex.getLine());
        assertEquals(1, ex.getColumn());
        assertTrue(ex.getMessage().contains("2 bytes"));
    }

    @Test
    void cadeiaAtributoStripsQuotes() {
        List<TokenInfo> infos = tokenize("\"ola mundo\"");
        assertEquals(new TokenInfo("CADEIA", "ola mundo", 1, 1), infos.get(0));
    }

    @Test
    void negativeCteIsTwoTokens() {
        List<TokenInfo> infos = tokenize("-5");
        assertEquals(2, infos.size());
        assertEquals(new TokenInfo("OPAD", "MENOS", 1, 1), infos.get(0));
        assertEquals(new TokenInfo("CTE",  "5",     1, 2), infos.get(1));
    }

    @Test
    void invalidCharacterThrowsLexicalExceptionWithLineAndColumn() {
        LexicalException ex = assertThrows(LexicalException.class, () -> tokenize("x := 10 @ 5"));
        assertEquals(1, ex.getLine());
        assertEquals(9, ex.getColumn());
    }

    @Test
    void commentBetweenSlashesIsSkipped() {
        List<TokenInfo> infos = tokenize("x /comentario/ y");
        assertEquals(2, infos.size());
        assertEquals("x", infos.get(0).atributo());
        assertEquals("y", infos.get(1).atributo());
    }

    @Test
    void whitespaceAndNewlinesAreSkipped() {
        List<TokenInfo> infos = tokenize("a\n  b\t\nc");
        assertEquals(3, infos.size());
        assertEquals(1, infos.get(0).line());
        assertEquals(2, infos.get(1).line());
        assertEquals(3, infos.get(2).line());
    }
}
