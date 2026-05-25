package com.pascalito.syntax;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SyntaxErrorListener extends BaseErrorListener {

    private final List<SyntaxError> errors = new ArrayList<>();

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line,
                            int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        errors.add(new SyntaxError(line, charPositionInLine + 1, msg));
    }

    public List<SyntaxError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
