# Especificação do Projeto — Construção de um Compilador

## 📦 Entregáveis

- **Analisador léxico**
- **Analisador sintático**
- **Analisador semântico**
- **Gerador de código**

## 🛠️ Ferramenta de Implementação

- **ANTLR4**
- Referências: Livro *The Definitive ANTLR4 Reference* e documentação em https://www.antlr.org/

---

## 📝 Descrição Léxica

### Palavras reservadas
`PROGRAM`, `INTEGER`, `BOOLEAN`, `BEGIN`, `END`, `WHILE`, `DO`, `READ`, `VAR`, `FALSE`, `TRUE`, `WRITE`

### Identificadores (ID)
- Sequência de **letras e números**, iniciando **obrigatoriamente por uma letra**
- Tamanho máximo: **16 caracteres** (caracteres adicionais devem ser descartados)
- Atributo: a própria cadeia de caracteres

### Constantes inteiras (CTE)
- Podem ter sinal (positivo, negativo) ou não
- Valor **não pode ultrapassar 2 bytes**

### Cadeias (CADEIA / STRING)
- Formadas por `"` + texto + `"`

### Operadores aritméticos

| Token | Tipo    | Atributo |
|-------|---------|----------|
| `+`   | OPAD    | MAIS     |
| `-`   | OPAD    | MENOS    |
| `*`   | OPMULT  | VEZES    |
| `/`   | OPMULT  | DIV      |

### Operadores lógicos

| Token | Tipo   | Atributo |
|-------|--------|----------|
| `OR`  | OPLOG  | OR       |
| `AND` | OPLOG  | AND      |
| `~`   | OPNEG  | NEG      |

### Operadores relacionais

| Token | Tipo   | Atributo |
|-------|--------|----------|
| `<`   | OPREL  | MENOR    |
| `<=`  | OPREL  | MENIG    |
| `>`   | OPREL  | MAIOR    |
| `>=`  | OPREL  | MAIG     |
| `==`  | OPREL  | IGUAL    |
| `<>`  | OPREL  | DIFER    |

### Símbolos

| Token | Tipo     |
|-------|----------|
| `;`   | PVIG     |
| `.`   | PONTO    |
| `:`   | DPONTOS  |
| `,`   | VIG      |
| `(`   | ABPAR    |
| `)`   | FPAR     |
| `:=`  | ATRIB    |

### Regras adicionais
- **Espaços em branco** entre tokens devem ser descartados
- A linguagem **não é case sensitive** (aceita maiúscula e minúscula sem diferenciação)
- **Comentários de linha**: delimitados por `/ /`, e seu conteúdo deve ser descartado
- **Erro léxico**: parar a execução e indicar na tela a **linha e/ou coluna** do erro
- **Sucesso**: imprimir os tokens identificados com seu **tipo** e **valor do atributo**

---

## 📐 Descrição Sintática

### Gramática original (com conflitos propositais)

```
Prog       --> PROGRAM IDENTIFIER PVIG Decls CmdComp PONTO
Decls      --> ε | VAR ListDecl
ListDecl   --> DeclTip | DeclTip ListDecl
DeclTip    --> ListId DPONTOS Tip PVIG
ListId     --> IDENTIFIER | IDENTIFIER VIG ListId
Tip        --> INTEGER | BOOLEAN | STRING

CmdComp    --> BEGIN ListCmd END
ListCmd    --> Cmd | Cmd PVIG ListCmd
Cmd        --> CmdIf | CmdWhile | CmdRead | CmdWrite | CmdAtrib | CmdComp

CmdIf      --> IF Expr THEN Cmd
             | IF Expr THEN Cmd ELSE Cmd

CmdWhile   --> WHILE Expr DO Cmd

CmdRead    --> READ ( ListId )
CmdWrite   --> WRITE ( ListW )
ListW      --> ElemW | ElemW VIG ListW
ElemW      --> Expr | CADEIA

CmdAtrib   --> IDENTIFIER := Expr

Expr       --> Expr OPREL Expr | Expr OPAD Expr | Expr OPMULT Expr
Expr       --> IDENTIFIER | CTE | ABPAR EXPR FPAR | TRUE | FALSE | OPNEG Expr
```

### Observações sobre os terminais e não-terminais
- Palavras em **maiúsculas** = terminais (declarados na análise léxica)
- Demais palavras = **não-terminais** (variáveis da gramática)

### Tarefas relacionadas à gramática
- **Identificar os conflitos** existentes (ex.: ambiguidades)
- **Modificar a gramática** de modo que não contenha conflitos
- **Escrever a nova gramática** (faz parte do trabalho)
- **Corrigir a aplicação do `OPNEG`**: na gramática original ele é aplicado a `Expr`, mas deveria ser aplicado apenas a `IDENTIFIER`, `CTE`, `ABPAR EXPR FPAR`, `TRUE`, `FALSE`, `OPNEG`. A eliminação dos conflitos deve resolver esse problema também.