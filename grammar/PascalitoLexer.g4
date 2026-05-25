lexer grammar PascalitoLexer;

options { caseInsensitive = true; }

// ===== Palavras reservadas =====
// Importante: devem vir antes da regra ID, senão são capturadas como identificador.
PROGRAM   : 'program' ;
VAR       : 'var' ;
BEGIN     : 'begin' ;
END       : 'end' ;
INTEGER   : 'integer' ;
BOOLEAN   : 'boolean' ;
STRING    : 'string' ;
IF        : 'if' ;
THEN      : 'then' ;
ELSE      : 'else' ;
WHILE     : 'while' ;
DO        : 'do' ;
READ      : 'read' ;
WRITE     : 'write' ;
TRUE      : 'true' ;
FALSE     : 'false' ;

// Operadores lógicos (palavras reservadas)
OR        : 'or' ;
AND       : 'and' ;

// ===== Operadores compostos =====
// Devem vir antes dos simples para o longest match funcionar corretamente.
ATRIB     : ':=' ;
MENIG     : '<=' ;
MAIG      : '>=' ;
DIFER     : '<>' ;
IGUAL     : '==' ;

// ===== Operadores aritméticos =====
MAIS      : '+' ;
MENOS     : '-' ;
VEZES     : '*' ;
DIV       : '/' ;

// ===== Operadores relacionais simples =====
MENOR     : '<' ;
MAIOR     : '>' ;

// ===== Operador de negação =====
NEG       : '~' ;

// ===== Símbolos =====
PVIG      : ';' ;
DPONTOS   : ':' ;
VIG       : ',' ;
PONTO     : '.' ;
ABPAR     : '(' ;
FPAR      : ')' ;

// ===== Identificadores e constantes =====
// Truncamento de ID em 16 caracteres é feito no pós-processamento (TokenPrinter).
// Validação de range da CTE (2 bytes: 0..32767) também é pós-processamento;
// o sinal é tratado como token OPAD separado.
ID        : [a-zA-Z][a-zA-Z0-9]* ;
CTE       : [0-9]+ ;
CADEIA    : '"' ~["\r\n]* '"' ;

// ===== Comentários e espaços (descartados) =====
// Comentário: '/' seguido de qualquer char (exceto '/' e quebra de linha) e '/'.
// Limitação documentada no relatório: dois '/' próximos numa expressão de divisão
// podem ser interpretados como comentário. Recomenda-se isolar com parênteses.
COMENTARIO : '/' ~[/\r\n]* '/' -> skip ;
WS         : [ \t\r\n]+ -> skip ;