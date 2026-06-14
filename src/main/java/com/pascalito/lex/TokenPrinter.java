package com.pascalito.lex;

import com.pascalito.parser.PascalitoLexer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenPrinter {

    public static final int ID_MAX_LENGTH = 16;

    private static final Map<Integer, String[]> EXPLICIT_CATEGORY = new HashMap<>();

    static {
        EXPLICIT_CATEGORY.put(PascalitoLexer.MAIS,   new String[]{"OPAD",   "MAIS"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.MENOS,  new String[]{"OPAD",   "MENOS"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.VEZES,  new String[]{"OPMULT", "VEZES"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.DIV,    new String[]{"OPMULT", "DIV"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.OR,     new String[]{"OPLOG",  "OR"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.AND,    new String[]{"OPLOG",  "AND"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.NEG,    new String[]{"OPNEG",  "NEG"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.MENOR,  new String[]{"OPREL",  "MENOR"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.MENIG,  new String[]{"OPREL",  "MENIG"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.MAIOR,  new String[]{"OPREL",  "MAIOR"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.MAIG,   new String[]{"OPREL",  "MAIG"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.IGUAL,  new String[]{"OPREL",  "IGUAL"});
        EXPLICIT_CATEGORY.put(PascalitoLexer.DIFER,  new String[]{"OPREL",  "DIFER"});
    }

    private final Vocabulary vocabulary;

    public TokenPrinter(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public List<TokenInfo> categorize(List<? extends Token> tokens) {
        return tokens.stream()
                .filter(t -> t.getType() != Token.EOF)
                .map(this::categorize)
                .toList();
    }

    public TokenInfo categorize(Token token) {
        int type = token.getType();
        int line = token.getLine();
        int column = token.getCharPositionInLine() + 1;

        String[] explicit = EXPLICIT_CATEGORY.get(type);
        if (explicit != null) {
            return new TokenInfo(explicit[0], explicit[1], line, column);
        }

        switch (type) {
            case PascalitoLexer.ID -> {
                String text = token.getText();
                String truncated = text.length() > ID_MAX_LENGTH
                        ? text.substring(0, ID_MAX_LENGTH)
                        : text;
                return new TokenInfo("ID", truncated, line, column);
            }
            case PascalitoLexer.CTE -> {
                // V2: o léxico apenas reconhece a sequência de dígitos. A validação do
                // limite de 2 bytes (overflow) é responsabilidade do analisador semântico.
                return new TokenInfo("CTE", token.getText(), line, column);
            }
            case PascalitoLexer.CADEIA -> {
                String text = token.getText();
                String content = text.substring(1, text.length() - 1);
                return new TokenInfo("CADEIA", content, line, column);
            }
            default -> {
                String name = vocabulary.getSymbolicName(type);
                if (name == null) {
                    name = vocabulary.getDisplayName(type);
                }
                return new TokenInfo(name, null, line, column);
            }
        }
    }

    public void print(List<TokenInfo> infos, PrintStream out) {
        out.printf("%-6s %-10s %-20s%n", "LINHA", "TIPO", "ATRIBUTO");
        out.printf("%-6s %-10s %-20s%n", "-----", "----------", "--------------------");
        for (TokenInfo info : infos) {
            out.printf("%-6d %-10s %-20s%n",
                    info.line(),
                    info.tipo(),
                    info.hasAtributo() ? info.atributo() : "-");
        }
    }
}
