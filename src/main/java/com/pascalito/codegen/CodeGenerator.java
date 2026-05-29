package com.pascalito.codegen;

import com.pascalito.parser.PascalitoParser;
import com.pascalito.parser.PascalitoParser.AtomContext;
import com.pascalito.parser.PascalitoParser.CmdAtribContext;
import com.pascalito.parser.PascalitoParser.CmdIfContext;
import com.pascalito.parser.PascalitoParser.CmdReadContext;
import com.pascalito.parser.PascalitoParser.CmdWhileContext;
import com.pascalito.parser.PascalitoParser.CmdWriteContext;
import com.pascalito.parser.PascalitoParser.DeclTipContext;
import com.pascalito.parser.PascalitoParser.ElemWContext;
import com.pascalito.parser.PascalitoParser.ExprAddContext;
import com.pascalito.parser.PascalitoParser.ExprAndContext;
import com.pascalito.parser.PascalitoParser.ExprMulContext;
import com.pascalito.parser.PascalitoParser.ExprOrContext;
import com.pascalito.parser.PascalitoParser.ExprRelContext;
import com.pascalito.parser.PascalitoParser.ExprUnaryContext;
import com.pascalito.parser.PascalitoParser.ProgContext;
import com.pascalito.parser.PascalitoParser.TipContext;
import com.pascalito.parser.PascalitoParserBaseVisitor;
import com.pascalito.semantic.SymbolTable;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CodeGenerator extends PascalitoParserBaseVisitor<String> {

    private final List<Instruction> code = new ArrayList<>();
    private int tempCounter = 0;
    private int labelCounter = 0;

    public List<Instruction> getCode() {
        return Collections.unmodifiableList(code);
    }

    private String novoTemp() {
        return "t" + (tempCounter++);
    }

    private String novoLabel(String prefix) {
        return "L_" + prefix + "_" + (labelCounter++);
    }

    private void emit(String op, String... args) {
        code.add(new Instruction(op, args));
    }

    private void emitComment(String text) {
        if (code.isEmpty()) {
            code.add(new Instruction("NOP").withComment(text));
            return;
        }
        Instruction last = code.remove(code.size() - 1);
        code.add(last.withComment(text));
    }

    // ===== Programa =====

    @Override
    public String visitProg(ProgContext ctx) {
        emit("PROG", ctx.ID().getText());
        if (ctx.decls() != null) {
            visit(ctx.decls());
        }
        visit(ctx.cmdComp());
        emit("HALT");
        return null;
    }

    @Override
    public String visitDeclTip(DeclTipContext ctx) {
        String typeName = typeName(ctx.tip());
        for (TerminalNode idNode : ctx.listId().ID()) {
            String name = SymbolTable.normalize(idNode.getText());
            emit("VAR", name, typeName);
        }
        return null;
    }

    private String typeName(TipContext ctx) {
        if (ctx.INTEGER() != null) return "INTEGER";
        if (ctx.BOOLEAN() != null) return "BOOLEAN";
        return "STRING";
    }

    // ===== Comandos =====

    @Override
    public String visitCmdAtrib(CmdAtribContext ctx) {
        String t = visit(ctx.expr());
        emit("STORE", SymbolTable.normalize(ctx.ID().getText()), t);
        return null;
    }

    @Override
    public String visitCmdIf(CmdIfContext ctx) {
        String tCond = visit(ctx.expr());
        boolean hasElse = ctx.ELSE() != null;
        String lElse = novoLabel("else");
        String lEnd  = novoLabel("endif");
        emit("JUMPF", tCond, hasElse ? lElse : lEnd);
        visit(ctx.cmd(0));
        if (hasElse) {
            emit("JUMP", lEnd);
            emit("LABEL", lElse);
            visit(ctx.cmd(1));
        }
        emit("LABEL", lEnd);
        return null;
    }

    @Override
    public String visitCmdWhile(CmdWhileContext ctx) {
        String lStart = novoLabel("while");
        String lEnd   = novoLabel("endwhile");
        emit("LABEL", lStart);
        String tCond = visit(ctx.expr());
        emit("JUMPF", tCond, lEnd);
        visit(ctx.cmd());
        emit("JUMP", lStart);
        emit("LABEL", lEnd);
        return null;
    }

    @Override
    public String visitCmdRead(CmdReadContext ctx) {
        for (TerminalNode idNode : ctx.listId().ID()) {
            emit("READ", SymbolTable.normalize(idNode.getText()));
        }
        return null;
    }

    @Override
    public String visitCmdWrite(CmdWriteContext ctx) {
        for (ElemWContext elem : ctx.listW().elemW()) {
            if (elem.CADEIA() != null) {
                String t = novoTemp();
                emit("LOAD", t, "#" + elem.CADEIA().getText());
                emit("WRITE", t);
            } else {
                String t = visit(elem.expr());
                emit("WRITE", t);
            }
        }
        return null;
    }

    // ===== Expressões =====

    @Override
    public String visitExprOr(ExprOrContext ctx) {
        return foldLeftBinary(ctx.exprAnd(), "OR");
    }

    @Override
    public String visitExprAnd(ExprAndContext ctx) {
        return foldLeftBinary(ctx.exprRel(), "AND");
    }

    @Override
    public String visitExprRel(ExprRelContext ctx) {
        List<ExprAddContext> parts = ctx.exprAdd();
        String left = visit(parts.get(0));
        if (parts.size() == 1) return left;
        String right = visit(parts.get(1));
        String op = relOpToMnemonic(ctx.relOp().getStart().getText());
        String t = novoTemp();
        emit(op, t, left, right);
        return t;
    }

    private String relOpToMnemonic(String src) {
        return switch (src) {
            case "<"  -> "LT";
            case "<=" -> "LE";
            case ">"  -> "GT";
            case ">=" -> "GE";
            case "==" -> "EQ";
            case "<>" -> "NEQ";
            default -> throw new IllegalStateException("Operador relacional desconhecido: " + src);
        };
    }

    @Override
    public String visitExprAdd(ExprAddContext ctx) {
        return foldAddMul(ctx);
    }

    @Override
    public String visitExprMul(ExprMulContext ctx) {
        return foldAddMul(ctx);
    }

    @Override
    public String visitExprUnary(ExprUnaryContext ctx) {
        if (ctx.atom() != null) {
            return visit(ctx.atom());
        }
        String inner = visit(ctx.exprUnary());
        if (ctx.NEG() != null) {
            String t = novoTemp();
            emit("NOT", t, inner);
            return t;
        }
        if (ctx.MENOS() != null) {
            String zero = novoTemp();
            emit("LOAD", zero, "#0");
            String t = novoTemp();
            emit("SUB", t, zero, inner);
            return t;
        }
        // unário '+' é no-op
        return inner;
    }

    @Override
    public String visitAtom(AtomContext ctx) {
        String t = novoTemp();
        if (ctx.ID() != null) {
            emit("LOAD", t, SymbolTable.normalize(ctx.ID().getText()));
            return t;
        }
        if (ctx.CTE() != null) {
            emit("LOAD", t, "#" + ctx.CTE().getText());
            return t;
        }
        if (ctx.TRUE() != null) {
            emit("LOAD", t, "#true");
            return t;
        }
        if (ctx.FALSE() != null) {
            emit("LOAD", t, "#false");
            return t;
        }
        // ( expr )
        return visit(ctx.expr());
    }

    // ===== Helpers =====

    private String foldLeftBinary(List<? extends ParserRuleContext> parts, String op) {
        String acc = visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            String right = visit(parts.get(i));
            String t = novoTemp();
            emit(op, t, acc, right);
            acc = t;
        }
        return acc;
    }

    private String foldAddMul(ParserRuleContext ctx) {
        var children = ctx.children;
        String acc = visit(children.get(0));
        for (int i = 1; i < children.size(); i += 2) {
            TerminalNode opNode = (TerminalNode) children.get(i);
            String opText = opNode.getSymbol().getText();
            String mnemonic = switch (opText) {
                case "+" -> "ADD";
                case "-" -> "SUB";
                case "*" -> "MUL";
                case "/" -> "DIV";
                default -> throw new IllegalStateException("Operador aritmético desconhecido: " + opText);
            };
            String right = visit(children.get(i + 1));
            String t = novoTemp();
            emit(mnemonic, t, acc, right);
            acc = t;
        }
        return acc;
    }
}
