grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}
COMMENT : '//' ~[\n\r]*  -> skip ;
MULTILINE : '/*' (.|'\n')*? '*/' -> skip;
CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT: 'import' ;
EXTENDS : 'extends';
DOTS: '...' ;
BOOLEAN: 'boolean' ;
INTEGER : [0-9] | [1-9][0-9]+ ;
ELSE: 'else';
TRUE: 'true';
FALSE: 'false';
THIS: 'this';
IF: 'if';
WHILE: 'while';
STATIC: 'static' ;
VOID: 'void' ;
STRING: 'String' ;
NEW: 'new';

ID : [a-zA-Z_$][a-zA-Z0-9_$]* ;

WS : [ \t\n\r\f]+ -> skip ;



importDecl
    : IMPORT packageName+=ID ('.' packageName+=ID)* ';'
    ;

program
    : (importDecl)* classNode=classDecl EOF
    ;

classDecl
    : CLASS name=ID
      (EXTENDS superName=ID)?
        '{'
        (varDecl)*
        (methodDecl)*
        '}'
    ;

varDecl
    : type name=ID ';'
    ;

type locals[boolean isArray=false, boolean isVararg=false]
    :
    ( name=INT
    | name= BOOLEAN
    | name= ID
    | name= STRING )
    ('[' ']' {$isArray=true;})?
    | name=INT DOTS    {$isVararg=true; $isArray=true;}
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    : ((PUBLIC {$isPublic=true;})?
            (STATIC {$isStatic=true;})
            VOID
            name=ID {$name.text.equals("main")}?
            '(' STRING '[' ']' args=ID ')'
            '{' varDecl* stmt* '}')
    | ( (PUBLIC {$isPublic=true;})?
         type
         name=ID
        '(' (param (',' param )* )?')'
        '{' varDecl* stmt* (returnStmt) '}')
    ;

returnStmt : RETURN expr ';' ;

param
    : typeNode=type name=ID
    ;



stmt
    : '{' ( stmt )* '}' #ScopeStmt
    | IF '(' cond=expr ')'  then=stmt ELSE elseStmt=stmt #IfStmt
    | WHILE '(' cond=expr ')' body=stmt #WhileStmt
    | expr ';' #ExprStmt
    | name=ID '[' expr ']' '=' expr ';' #ArrayAssignStmt
    | expr '=' expr ';' #AssignStmt
    ;

expr
    : value=INTEGER  #IntegerLiteral
    | value=TRUE #BooleanLiteral
    | value=FALSE #BooleanLiteral
    | name=ID #VarRefExpr
    | value= THIS #This
    | value='[' ( expr ( ',' expr)* )? ']' #ArrayInit
    | expression= expr '[' inside=expr ']' #ArrayAccess
    | expr '.' name=ID {$name.text.equals("length")}? #ArrayLength
    | expr '.' name=ID '(' (expr (',' expr)*)? ')'  #MethodCall
    | '(' expression=expr ')' #Paren
    | NEW name=ID '(' ')' #NewClass
    | NEW INT '['expression= expr ']' #NewArray
    | '!' expression= expr #Not
    | left= expr op= ('*' | '/') right= expr  #BinaryExpr
    | left= expr op= ('+' | '-' ) right= expr #BinaryExpr
    | left= expr op= ('<' | '>') right= expr  #BinaryExpr
    | left= expr op='&&' right= expr #BinaryExpr
    ;



