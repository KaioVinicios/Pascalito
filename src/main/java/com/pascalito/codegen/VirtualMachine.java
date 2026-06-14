package com.pascalito.codegen;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VirtualMachine {

    private final List<Instruction> code;
    private final BufferedReader in;
    private final PrintWriter out;

    private final Map<String, Object> vars = new HashMap<>();
    private final Map<String, String> varTypes = new HashMap<>();
    private final Map<String, Object> temps = new HashMap<>();
    private final Map<String, Integer> labels = new HashMap<>();

    public VirtualMachine(List<Instruction> code, Reader in, PrintWriter out) {
        this.code = code;
        this.in = new BufferedReader(in);
        this.out = out;
    }

    public static VirtualMachine stdio(List<Instruction> code) {
        return new VirtualMachine(code,
                new java.io.InputStreamReader(System.in),
                new PrintWriter(new java.io.OutputStreamWriter(System.out), true));
    }

    public void run() {
        indexLabels();
        int pc = 0;
        while (pc < code.size()) {
            Instruction ins = code.get(pc);
            switch (ins.op()) {
                case "PROG", "LABEL", "NOP" -> {}
                case "VAR" -> {
                    String name = ins.args().get(0);
                    String type = ins.args().get(1);
                    varTypes.put(name, type);
                    vars.put(name, defaultValue(type));
                }
                case "LOAD"  -> temps.put(ins.args().get(0), readSource(ins.args().get(1)));
                case "STORE" -> {
                    String name = ins.args().get(0);
                    Object v = temps.get(ins.args().get(1));
                    vars.put(name, v);
                }
                case "ADD" -> binIntOp(ins, (a, b) -> a + b);
                case "SUB" -> binIntOp(ins, (a, b) -> a - b);
                case "MUL" -> binIntOp(ins, (a, b) -> a * b);
                case "DIV" -> binIntOp(ins, (a, b) -> {
                    if (b == 0) throw new VmException("Divisão por zero");
                    return a / b;
                });
                case "LT"  -> cmpOp(ins, (a, b) -> a <  b);
                case "LE"  -> cmpOp(ins, (a, b) -> a <= b);
                case "GT"  -> cmpOp(ins, (a, b) -> a >  b);
                case "GE"  -> cmpOp(ins, (a, b) -> a >= b);
                case "EQ"  -> eqOp(ins, true);
                case "NEQ" -> eqOp(ins, false);
                case "AND" -> boolOp(ins, (a, b) -> a && b);
                case "OR"  -> boolOp(ins, (a, b) -> a || b);
                case "NOT" -> temps.put(ins.args().get(0), !((Boolean) temps.get(ins.args().get(1))));
                case "JUMP"  -> { pc = labels.get(ins.args().get(0)); continue; }
                case "JUMPF" -> {
                    boolean cond = (Boolean) temps.get(ins.args().get(0));
                    if (!cond) { pc = labels.get(ins.args().get(1)); continue; }
                }
                case "READ"  -> readInto(ins.args().get(0));
                case "WRITE" -> out.println(format(temps.get(ins.args().get(0))));
                case "HALT"  -> { out.flush(); return; }
                default -> throw new VmException("Instrução desconhecida: " + ins.op());
            }
            pc++;
        }
        out.flush();
    }

    private void indexLabels() {
        for (int i = 0; i < code.size(); i++) {
            Instruction ins = code.get(i);
            if (ins.isLabel()) labels.put(ins.args().get(0), i);
        }
    }

    private Object defaultValue(String type) {
        return switch (type) {
            case "INTEGER" -> 0;
            case "BOOLEAN" -> false;
            case "STRING"  -> "";
            default -> throw new VmException("Tipo desconhecido: " + type);
        };
    }

    private Object readSource(String src) {
        if (src.startsWith("#")) {
            String lit = src.substring(1);
            if (lit.startsWith("\"")) {
                return lit.substring(1, lit.length() - 1);
            }
            if ("true".equals(lit))  return Boolean.TRUE;
            if ("false".equals(lit)) return Boolean.FALSE;
            return Integer.parseInt(lit);
        }
        // Temporárias virtuais da IR 3AC têm o prefixo "t_"; identificadores de
        // variável nunca contêm '_' (gramática ID: [a-zA-Z][a-zA-Z0-9]*).
        if (src.startsWith("t_")) {
            return temps.get(src);
        }
        return vars.get(src);
    }

    private void binIntOp(Instruction ins, java.util.function.IntBinaryOperator op) {
        int a = (Integer) temps.get(ins.args().get(1));
        int b = (Integer) temps.get(ins.args().get(2));
        temps.put(ins.args().get(0), op.applyAsInt(a, b));
    }

    private void cmpOp(Instruction ins, java.util.function.BiPredicate<Integer, Integer> op) {
        int a = (Integer) temps.get(ins.args().get(1));
        int b = (Integer) temps.get(ins.args().get(2));
        temps.put(ins.args().get(0), op.test(a, b));
    }

    private void eqOp(Instruction ins, boolean eq) {
        Object a = temps.get(ins.args().get(1));
        Object b = temps.get(ins.args().get(2));
        boolean result = java.util.Objects.equals(a, b);
        temps.put(ins.args().get(0), eq == result);
    }

    private void boolOp(Instruction ins, java.util.function.BinaryOperator<Boolean> op) {
        boolean a = (Boolean) temps.get(ins.args().get(1));
        boolean b = (Boolean) temps.get(ins.args().get(2));
        temps.put(ins.args().get(0), op.apply(a, b));
    }

    private void readInto(String name) {
        String type = varTypes.get(name);
        if (type == null) throw new VmException("READ em variável não declarada: " + name);
        String line;
        try {
            line = in.readLine();
        } catch (java.io.IOException e) {
            throw new VmException("Falha lendo stdin: " + e.getMessage());
        }
        if (line == null) throw new VmException("EOF inesperado no READ");
        switch (type) {
            case "INTEGER" -> vars.put(name, Integer.parseInt(line.trim()));
            case "BOOLEAN" -> vars.put(name, Boolean.parseBoolean(line.trim()));
            case "STRING"  -> vars.put(name, line);
            default -> throw new VmException("Tipo inválido para READ: " + type);
        }
    }

    private String format(Object v) {
        if (v instanceof Boolean b) return b ? "true" : "false";
        return String.valueOf(v);
    }

    public static class VmException extends RuntimeException {
        public VmException(String msg) { super(msg); }
    }
}
