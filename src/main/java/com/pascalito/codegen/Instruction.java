package com.pascalito.codegen;

import java.util.Arrays;
import java.util.List;

public record Instruction(String op, List<String> args, String comment) {

    public Instruction(String op, String... args) {
        this(op, List.of(args), null);
    }

    public Instruction withComment(String c) {
        return new Instruction(op, args, c);
    }

    public boolean isLabel() {
        return "LABEL".equals(op);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isLabel()) {
            sb.append(args.get(0)).append(":");
        } else {
            sb.append("    ").append(op);
            if (!args.isEmpty()) {
                sb.append(" ").append(String.join(", ", args));
            }
        }
        if (comment != null && !comment.isEmpty()) {
            while (sb.length() < 32) sb.append(' ');
            sb.append(" ; ").append(comment);
        }
        return sb.toString();
    }

    public static Instruction of(String op, String... args) {
        return new Instruction(op, Arrays.asList(args), null);
    }
}
