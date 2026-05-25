parser grammar PascalitoParser;

options { tokenVocab = PascalitoLexer; }

// ===== Estrutura do programa =====
prog        : PROGRAM ID PVIG decls cmdComp PONTO EOF ;

decls       : (VAR declTip+)? ;
declTip     : listId DPONTOS tip PVIG ;
listId      : ID (VIG ID)* ;
tip         : INTEGER | BOOLEAN | STRING ;

// ===== Comandos =====
cmdComp     : BEGIN listCmd END ;
listCmd     : cmd (PVIG cmd)* ;

cmd         : cmdIf
            | cmdWhile
            | cmdRead
            | cmdWrite
            | cmdAtrib
            | cmdComp
            ;

cmdIf       : IF expr THEN cmd (ELSE cmd)? ;
cmdWhile    : WHILE expr DO cmd ;
cmdRead     : READ ABPAR listId FPAR ;
cmdWrite    : WRITE ABPAR listW FPAR ;
listW       : elemW (VIG elemW)* ;
elemW       : expr | CADEIA ;
cmdAtrib    : ID ATRIB expr ;

// ===== Expressões (estratificadas por precedência) =====
// Da menor para a maior precedência:
//   or  <  and  <  rel  <  add  <  mul  <  unário  <  átomo
// Relacionais NÃO são associativos (proíbe a < b < c).
expr        : exprOr ;
exprOr      : exprAnd  (OR  exprAnd)* ;
exprAnd     : exprRel  (AND exprRel)* ;
exprRel     : exprAdd  (relOp exprAdd)? ;
exprAdd     : exprMul  ((MAIS | MENOS) exprMul)* ;
exprMul     : exprUnary ((VEZES | DIV) exprUnary)* ;
exprUnary   : (NEG | MAIS | MENOS) exprUnary
            | atom
            ;
atom        : ID
            | CTE
            | TRUE
            | FALSE
            | ABPAR expr FPAR
            ;
relOp       : MENOR | MENIG | MAIOR | MAIG | IGUAL | DIFER ;
