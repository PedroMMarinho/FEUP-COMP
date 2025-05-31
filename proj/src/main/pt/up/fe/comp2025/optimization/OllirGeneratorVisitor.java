package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    private String currentMethod;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table, ollirTypes);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(SCOPE_STMT, this::visitScopeStmt);

        //   setDefaultVisit(this::defaultVisit);
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var cond = exprVisitor.visit(node.getChild(0));

        code.append(cond.getComputation());
        code.append("if (");
        code.append(cond.getCode());
        code.append(") goto ");
        var thenLabel = ollirTypes.nextThenLabel();
        var endLabel = ollirTypes.nextEndLabel();
        code.append(thenLabel);
        code.append(END_STMT);

        var end = visit(node.getChild(2));
        code.append(end);

        code.append("goto ");
        code.append(endLabel);
        code.append(END_STMT);

        code.append(thenLabel);
        code.append(":\n");

        var then = visit(node.getChild(1));
        code.append(then);

        code.append(endLabel);
        code.append(":\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var whileLabel = ollirTypes.nextWhileLabel();
        code.append("%s:\n".formatted(whileLabel));

        var cond = exprVisitor.visit(node.getChild(0));
        code.append(cond.getComputation());
        var endLabel = ollirTypes.nextEndLabel();
        code.append("if (!.bool %s) goto %s".formatted(cond.getCode(),endLabel));
        code.append(END_STMT);

        var body = visit(node.getChild(1));
        code.append(body);

        code.append("goto %s".formatted(whileLabel));
        code.append(END_STMT);
        code.append("%s:\n".formatted(endLabel));
        return code.toString();
    }

    private String visitArrayAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var arrayName = node.get("name");
        Symbol symbol = new Symbol(TypeUtils.newIntArrayType(),arrayName);
        if (!table.getLocalVariables(currentMethod).contains(symbol) && !table.getParameters(currentMethod).contains(symbol) && table.getFields().contains(symbol)) {
            var tmp = ollirTypes.nextTemp();
            code.append("%s.array.i32 :=.array.i32 getfield(this, %s.array.i32).array.i32;\n".formatted(tmp,arrayName));
            arrayName = tmp + ".array.i32";
        }

        var lhs = exprVisitor.visit(node.getChild(0));
        var rhs = exprVisitor.visit(node.getChild(1));

        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        code.append("%s[%s].i32 :=.i32 %s;\n".formatted(arrayName,lhs.getCode(),rhs.getCode()));
        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var expr = exprVisitor.visit(node.getChild(0));

        StringBuilder code = new StringBuilder();
        code.append(expr.getComputation());

        return code.toString();
    }

    private String visitScopeStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for( var child: node.getChildren()){
            code.append(visit(child));
        }

        return code.toString();
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Check if RHS is a simple binary expression that can be optimized
        var rhsNode = node.getChild(1);
        var left = node.getChild(0);
        var id = left.get("name");
        Type type = types.getExprType(left, currentMethod);
        String typeString = ollirTypes.toOllirType(type);

        // Check if we can optimize: only simple binary expressions with simple operands
        boolean canOptimize = isSimpleBinaryExpression(rhsNode);

        Symbol symbol = new Symbol(type, id);
        boolean isField = !table.getLocalVariables(currentMethod).contains(symbol) &&
                !table.getParameters(currentMethod).contains(symbol) &&
                table.getFields().contains(symbol);

        if (canOptimize && !isField) {
            // For simple binary operations on local variables, optimize
            exprVisitor.setDirectAssignment(true);
            var rhs = exprVisitor.visit(rhsNode);
            exprVisitor.setDirectAssignment(false);

            code.append(rhs.getComputation());
            code.append("%s%s :=%s %s;\n".formatted(id, typeString, typeString, rhs.getCode()));
        } else {
            // Use the original approach for fields and complex expressions
            var rhs = exprVisitor.visit(rhsNode);
            code.append(rhs.getComputation());

            if (isField) {
                code.append("putfield(this, %s%s, %s).V;\n".formatted(id, typeString, rhs.getCode()));
            } else {
                code.append("%s%s :=%s %s;\n".formatted(id, typeString, typeString, rhs.getCode()));
            }
        }

        return code.toString();
    }

    /**
     * Checks if a node represents a simple binary expression that can be directly assigned
     * without creating an unnecessary temporary variable.
     */
    private boolean isSimpleBinaryExpression(JmmNode node) {
        if (!node.getKind().equals(BINARY_EXPR.toString())) {
            return false;
        }

        // Don't optimize short-circuit operations
        if (node.get("op").equals("&&")) {
            return false;
        }

        // Check if both operands are simple (variables or literals)
        JmmNode left = node.getChild(0);
        JmmNode right = node.getChild(1);

        return isSimpleOperand(left) && isSimpleOperand(right);
    }

    /**
     * Checks if a node is a simple operand (variable reference or literal)
     */
    private boolean isSimpleOperand(JmmNode node) {
        String kind = node.getKind();
        return kind.equals(VAR_REF_EXPR.toString()) ||
                kind.equals(INTEGER_LITERAL.toString()) ||
                kind.equals(BOOLEAN_LITERAL.toString()) ||
                kind.equals(THIS.toString()) ||
                kind.equals(PAREN.toString()); // Parentheses around simple expressions
    }


    private String visitReturn(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;

        Type retType = types.getExprType(node.getChild(0), currentMethod);

        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);

        if (isPublic) {
            code.append("public ");
        }

        boolean isStatic = node.getBoolean("isStatic", false);

        if (isStatic) {
            code.append("static ");
        }

        var params = node.getChildren(PARAM);
        // varargs
        boolean doubleVarargs = false;
        if (!params.isEmpty()){
            var lastParam = TypeUtils.convertType(params.getLast().getChild(0));

            if (TypeUtils.isVararg(lastParam)){
                if( node.get("name").equals("varargs")){
                    doubleVarargs = true;
                }
                code.append("varargs ");
            }
        }

        var name = node.get("name");
        if(doubleVarargs){
            code.append("\"varargs\"");
        }
        else {
            code.append(name);
        }

        // params
        code.append("(");
        for (int i = 0; i < params.size(); i++) {
            var result = visit(params.get(i));
            code.append(result);
            if (i != params.size()-1){
                code.append(", ");
            }
            else {

            }
        }

        if (name.equals("main")){
            code.append(node.get("args"));
            code.append(".array.String");
        }
        code.append(")");

        // type
        Type returnType = table.getReturnType(name);
        String retTypeCode = ollirTypes.toOllirType(returnType);
        code.append(retTypeCode);
        code.append(L_BRACKET);

        exprVisitor.setCurrentMethod(name);
        this.currentMethod = name;

        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        var returnCode = node.getChildren(RETURN_STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(returnCode);

        if (name.equals("main")){
            code.append("ret.V;");
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        String superName = table.getSuper();
        if (superName != null){
            code.append(" extends ");
            code.append(superName);
        }

        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);

        for (var field : table.getFields()) {
            Type fieldType = field.getType();
            String fieldName = field.getName();

            String fieldCode = ".field private %s%s".formatted(fieldName, ollirTypes.toOllirType(fieldType));

            code.append(fieldCode);
            code.append(END_STMT);
        }

        code.append(NL);

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }

    private String visitImport(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder("import ");
        var p = node.getObjectAsList("packageName");
        code.append(p.get(0));
        for (var name : p.subList(1,p.size())){
            code.append(".");
            code.append(name);
        }
        code.append(END_STMT);
        return code.toString();
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        System.out.println(code.toString());
        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}