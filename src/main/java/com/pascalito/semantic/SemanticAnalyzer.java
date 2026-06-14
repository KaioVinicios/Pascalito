package com.pascalito.semantic;

import com.pascalito.parser.PascalitoParser;
import com.pascalito.parser.PascalitoParser.AtomContext;
import com.pascalito.parser.PascalitoParser.CmdAtribContext;
import com.pascalito.parser.PascalitoParser.CmdCompContext;
import com.pascalito.parser.PascalitoParser.CmdIfContext;
import com.pascalito.parser.PascalitoParser.CmdReadContext;
import com.pascalito.parser.PascalitoParser.CmdWhileContext;
import com.pascalito.parser.PascalitoParser.DeclTipContext;
import com.pascalito.parser.PascalitoParser.ExprAddContext;
import com.pascalito.parser.PascalitoParser.ExprAndContext;
import com.pascalito.parser.PascalitoParser.ExprMulContext;
import com.pascalito.parser.PascalitoParser.ExprOrContext;
import com.pascalito.parser.PascalitoParser.ExprRelContext;
import com.pascalito.parser.PascalitoParser.ExprUnaryContext;
import com.pascalito.parser.PascalitoParser.TipContext;
import com.pascalito.parser.PascalitoParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SemanticAnalyzer extends PascalitoParserBaseVisitor<Type> {

    /** Escopo global (raiz da cadeia); contém as declarações de nível de programa. */
    private final SymbolTable globalScope = new SymbolTable();
    /** Escopo corrente; avança para filhos a cada BEGIN/END. */
    private SymbolTable symbols = globalScope;
    private final List<SemanticError> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /** Faixa de valores de uma CTE (inteiro de 2 bytes COM sinal). */
    private static final int CTE_MIN = -32768;
    private static final int CTE_MAX = 32767;

    public List<SemanticError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /** Avisos não-fatais (ex.: truncamento de identificador longo) emitidos pela análise. */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public SymbolTable getSymbolTable() {
        return globalScope;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // ===== Declarações =====

    @Override
    public Type visitDeclTip(DeclTipContext ctx) {
        Type declared = typeOf(ctx.tip());
        for (TerminalNode idNode : ctx.listId().ID()) {
            String raw = idNode.getText();
            String name = SymbolTable.normalize(raw);
            Token tok = idNode.getSymbol();
            int line = tok.getLine();
            int col = tok.getCharPositionInLine() + 1;
            warnIfTruncated(raw, name, line, col);
            Symbol existing = symbols.lookupLocal(name);
            if (existing != null) {
                error(line, col,
                        "Identificador '%s' já declarado na linha %d"
                                .formatted(name, existing.line()));
            } else {
                symbols.declare(new Symbol(name, declared, line, col));
            }
        }
        return null;
    }

    private Type typeOf(TipContext ctx) {
        if (ctx.INTEGER() != null) return Type.INTEGER;
        if (ctx.BOOLEAN() != null) return Type.BOOLEAN;
        if (ctx.STRING() != null) return Type.STRING;
        return Type.ERROR;
    }

    // ===== Comandos =====

    /**
     * Cada bloco {@code BEGIN}/{@code END} abre um escopo filho encadeado ao escopo
     * corrente e o fecha ao final, restaurando o escopo pai. Declarações locais (caso
     * a linguagem venha a permiti-las) ficam confinadas ao bloco e podem sombrear o pai.
     */
    @Override
    public Type visitCmdComp(CmdCompContext ctx) {
        symbols = new SymbolTable(symbols);
        try {
            return visitChildren(ctx);
        } finally {
            symbols = symbols.parent();
        }
    }

    @Override
    public Type visitCmdAtrib(CmdAtribContext ctx) {
        TerminalNode idNode = ctx.ID();
        String name = SymbolTable.normalize(idNode.getText());
        Token tok = idNode.getSymbol();
        int line = tok.getLine();
        int col = tok.getCharPositionInLine() + 1;

        Type exprType = visit(ctx.expr());
        Symbol sym = symbols.lookup(name);

        if (sym == null) {
            error(line, col, "Variável '%s' não declarada".formatted(name));
            return null;
        }
        if (sym.type() == Type.STRING) {
            // Atribuição direta a string já é bloqueada pelo parser; defesa em profundidade.
            error(line, col,
                    "Variáveis 'string' não podem ser atribuídas; use 'read(%s)'".formatted(name));
            return null;
        }
        if (exprType != Type.ERROR && exprType != sym.type()) {
            error(line, col,
                    "Atribuição incompatível: '%s' é %s, expressão é %s"
                            .formatted(name, sym.type().displayName(), exprType.displayName()));
        }
        return null;
    }

    @Override
    public Type visitCmdIf(CmdIfContext ctx) {
        Type cond = visit(ctx.expr());
        if (cond != Type.ERROR && cond != Type.BOOLEAN) {
            Token tok = ctx.IF().getSymbol();
            error(tok.getLine(), tok.getCharPositionInLine() + 1,
                    "Condição do 'if' deve ser boolean, encontrado %s".formatted(cond.displayName()));
        }
        ctx.cmd().forEach(this::visit);
        return null;
    }

    @Override
    public Type visitCmdWhile(CmdWhileContext ctx) {
        Type cond = visit(ctx.expr());
        if (cond != Type.ERROR && cond != Type.BOOLEAN) {
            Token tok = ctx.WHILE().getSymbol();
            error(tok.getLine(), tok.getCharPositionInLine() + 1,
                    "Condição do 'while' deve ser boolean, encontrado %s".formatted(cond.displayName()));
        }
        visit(ctx.cmd());
        return null;
    }

    @Override
    public Type visitCmdRead(CmdReadContext ctx) {
        for (TerminalNode idNode : ctx.listId().ID()) {
            String name = SymbolTable.normalize(idNode.getText());
            Token tok = idNode.getSymbol();
            if (symbols.lookup(name) == null) {
                error(tok.getLine(), tok.getCharPositionInLine() + 1,
                        "Variável '%s' não declarada".formatted(name));
            }
        }
        return null;
    }

    // visitCmdWrite: write aceita qualquer expr ou CADEIA — apenas verifica expressões internas
    // (a verificação de tipo das expressões é feita por visitChildren padrão)

    // ===== Expressões =====

    @Override
    public Type visitExprOr(ExprOrContext ctx) {
        List<ExprAndContext> parts = ctx.exprAnd();
        Type result = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            Type right = visit(parts.get(i));
            result = checkLogical(result, right, "or", ctx.OR(i - 1).getSymbol());
        }
        return result;
    }

    @Override
    public Type visitExprAnd(ExprAndContext ctx) {
        List<ExprRelContext> parts = ctx.exprRel();
        Type result = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            Type right = visit(parts.get(i));
            result = checkLogical(result, right, "and", ctx.AND(i - 1).getSymbol());
        }
        return result;
    }

    @Override
    public Type visitExprRel(ExprRelContext ctx) {
        List<ExprAddContext> parts = ctx.exprAdd();
        Type left = visit(parts.get(0));
        if (parts.size() == 1) {
            return left;
        }
        Type right = visit(parts.get(1));
        Token op = ctx.relOp().getStart();
        if (left == Type.ERROR || right == Type.ERROR) {
            return Type.ERROR;
        }
        if (left != right) {
            error(op.getLine(), op.getCharPositionInLine() + 1,
                    "Operador relacional '%s' exige operandos do mesmo tipo (%s vs %s)"
                            .formatted(op.getText(), left.displayName(), right.displayName()));
            return Type.ERROR;
        }
        if (left == Type.STRING) {
            error(op.getLine(), op.getCharPositionInLine() + 1,
                    "Operador relacional '%s' não suportado para strings".formatted(op.getText()));
            return Type.ERROR;
        }
        return Type.BOOLEAN;
    }

    @Override
    public Type visitExprAdd(ExprAddContext ctx) {
        return foldArithmetic(ctx);
    }

    @Override
    public Type visitExprMul(ExprMulContext ctx) {
        return foldArithmetic(ctx);
    }

    @Override
    public Type visitExprUnary(ExprUnaryContext ctx) {
        if (ctx.atom() != null) {
            return visit(ctx.atom());
        }
        Type inner = visit(ctx.exprUnary());
        Token op = (ctx.NEG() != null) ? ctx.NEG().getSymbol()
                : (ctx.MAIS() != null) ? ctx.MAIS().getSymbol()
                : ctx.MENOS().getSymbol();
        boolean isNeg = ctx.NEG() != null;

        if (inner == Type.ERROR) return Type.ERROR;
        if (isNeg) {
            if (inner != Type.BOOLEAN) {
                error(op.getLine(), op.getCharPositionInLine() + 1,
                        "Operador '~' exige operando boolean, encontrado %s".formatted(inner.displayName()));
                return Type.ERROR;
            }
            return Type.BOOLEAN;
        } else {
            if (inner != Type.INTEGER) {
                error(op.getLine(), op.getCharPositionInLine() + 1,
                        "Operador unário '%s' exige operando integer, encontrado %s"
                                .formatted(op.getText(), inner.displayName()));
                return Type.ERROR;
            }
            return Type.INTEGER;
        }
    }

    @Override
    public Type visitAtom(AtomContext ctx) {
        if (ctx.ID() != null) {
            String name = SymbolTable.normalize(ctx.ID().getText());
            Token tok = ctx.ID().getSymbol();
            Symbol sym = symbols.lookup(name);
            if (sym == null) {
                error(tok.getLine(), tok.getCharPositionInLine() + 1,
                        "Variável '%s' não declarada".formatted(name));
                return Type.ERROR;
            }
            return sym.type();
        }
        if (ctx.CTE() != null)   { checkCteRange(ctx); return Type.INTEGER; }
        if (ctx.TRUE() != null)  return Type.BOOLEAN;
        if (ctx.FALSE() != null) return Type.BOOLEAN;
        if (ctx.expr() != null)  return visit(ctx.expr());
        return Type.ERROR;
    }

    // ===== Helpers =====

    /**
     * Itera os children de exprAdd/exprMul no formato
     *   [expr (OP expr)*]
     * Aplica cada operador binário sequencialmente.
     */
    private Type foldArithmetic(org.antlr.v4.runtime.ParserRuleContext ctx) {
        var children = ctx.children;
        Type result = visit(children.get(0));
        for (int i = 1; i < children.size(); i += 2) {
            TerminalNode opNode = (TerminalNode) children.get(i);
            Token op = opNode.getSymbol();
            Type right = visit(children.get(i + 1));
            if (result == Type.ERROR || right == Type.ERROR) {
                result = Type.ERROR;
                continue;
            }
            if (result != Type.INTEGER || right != Type.INTEGER) {
                error(op.getLine(), op.getCharPositionInLine() + 1,
                        "Operador '%s' exige operandos integer (encontrados %s e %s)"
                                .formatted(op.getText(), result.displayName(), right.displayName()));
                result = Type.ERROR;
            } else {
                result = Type.INTEGER;
            }
        }
        return result;
    }

    private Type checkLogical(Type left, Type right, String opName, Token op) {
        if (left == Type.ERROR || right == Type.ERROR) return Type.ERROR;
        if (left != Type.BOOLEAN || right != Type.BOOLEAN) {
            error(op.getLine(), op.getCharPositionInLine() + 1,
                    "Operador '%s' exige operandos boolean (encontrados %s e %s)"
                            .formatted(opName, left.displayName(), right.displayName()));
            return Type.ERROR;
        }
        return Type.BOOLEAN;
    }

    /**
     * Valida a faixa de uma constante inteira (2 bytes com sinal). A magnitude vem do
     * token {@code CTE} (sempre sem sinal na gramática); o sinal vem de um {@code MENOS}
     * unário aplicado diretamente à constante, permitindo o limite inferior {@code -32768}.
     */
    private void checkCteRange(AtomContext ctx) {
        Token tok = ctx.CTE().getSymbol();
        boolean negated = isDirectlyNegated(ctx);
        long magnitude;
        try {
            magnitude = Long.parseLong(tok.getText());
        } catch (NumberFormatException e) {
            magnitude = Long.MAX_VALUE; // grande demais até para long → estoura de qualquer forma
        }
        long signed = negated ? -magnitude : magnitude;
        if (signed < CTE_MIN || signed > CTE_MAX) {
            error(tok.getLine(), tok.getCharPositionInLine() + 1,
                    "Overflow de Constante: %s%s fora do intervalo [%d, %d]"
                            .formatted(negated ? "-" : "", tok.getText(), CTE_MIN, CTE_MAX));
        }
    }

    /** Verifica se o {@code atom} está sob um {@code MENOS} unário aplicado diretamente. */
    private boolean isDirectlyNegated(AtomContext ctx) {
        return ctx.getParent() instanceof ExprUnaryContext inner
                && inner.getParent() instanceof ExprUnaryContext outer
                && outer.MENOS() != null
                && outer.exprUnary() == inner;
    }

    private void warnIfTruncated(String raw, String truncated, int line, int col) {
        if (raw.length() > SymbolTable.ID_MAX_LENGTH) {
            warnings.add(
                    "Aviso (linha %d, coluna %d): identificador '%s' truncado para '%s' (máx. %d caracteres)"
                            .formatted(line, col, raw, truncated, SymbolTable.ID_MAX_LENGTH));
        }
    }

    private void error(int line, int column, String message) {
        errors.add(new SemanticError(line, column, message));
    }
}
