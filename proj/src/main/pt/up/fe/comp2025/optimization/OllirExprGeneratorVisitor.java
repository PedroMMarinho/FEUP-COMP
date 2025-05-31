package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;

import pt.up.fe.comp2025.ast.TypeUtils;


import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    private String currentMethod;
    private boolean isDirectAssignment = false; // Flag to track direct assignment context

    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils ollirTypes) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = ollirTypes;
    }

    public void setCurrentMethod(String name){
        this.currentMethod = name;
    }

    public void setDirectAssignment(boolean directAssignment) {
        this.isDirectAssignment = directAssignment;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(NOT, this::visitNot); // ??
        addVisit(THIS, this::visitThis); // ??
        addVisit(PAREN, this::visitParen);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(NEW_CLASS, this::visitNewClass);
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(ARRAY_INIT, this::visitArrayInit);
        addVisit(ARRAY_ACCESS, this::visitArrayAcess);
        addVisit(ARRAY_LENGTH, this::visitArrayLength);
        // setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused){
        var booleanType = TypeUtils.newBooleanType();
        String ollirBooleanType = ollirTypes.toOllirType(booleanType);
        String code = "";
        if (node.get("value").equals("true")){
            code += "1";
        }
        else{
            code += "0";
        }
        code += ollirBooleanType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitNot(JmmNode node, Void unused){
        var expr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());

        String resOllirType = ".bool";
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("!").append(resOllirType).append(SPACE)
                .append(expr.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitThis(JmmNode node, Void unused){
        String code = "this." + table.getClassName();
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParen(JmmNode node, Void unused){
        return visit(node.getChild(0));
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused){
        StringBuilder computation = new StringBuilder();

        var caller = node.getChild(0);
        var callerRes = visit(caller);
        computation.append(callerRes.getComputation());
        var methodName = node.get("name");

        String argsCode = "";
        for (var args : node.getChildren().subList(1,node.getNumChildren())){
            var arg = visit(args);
            computation.append(arg.getComputation());
            argsCode += ", " + arg.getCode();
        }

        String type = ".V";
        if (caller.getKind().equals(THIS.toString())) {
            type = ollirTypes.toOllirType(types.getExprType(node,currentMethod));
        } else if (node.getParent().getKind().equals(RETURN_STMT.toString())) {
            type = ollirTypes.toOllirType(table.getReturnType(currentMethod));
        } else if (node.getParent().getKind().equals(ASSIGN_STMT.toString()) ) {
            type = ollirTypes.toOllirType(types.getExprType(node.getParent().getChild(0),currentMethod));
        } else if (node.getParent().getKind().equals(ARRAY_ASSIGN_STMT.toString())) {
            type = ollirTypes.toOllirType(types.getVarRefType(node.getParent().get("name"), currentMethod));
        } else if (types.getExprType(caller,currentMethod).getName().equals(table.getClassName())) {
            type = ollirTypes.toOllirType(table.getReturnType(methodName));
        }

        String code = "";
        if(!node.getParent().getKind().equals(EXPR_STMT.toString())){
            var temp = ollirTypes.nextTemp();
            code = temp + type;
            computation.append("%s :=%s ".formatted(code,type));
        }

        var isVirtual = caller.getKind().equals(THIS.toString());
        if(!isVirtual){
            Symbol symbol = new Symbol(types.getExprType(caller,currentMethod),caller.get("name"));

            isVirtual = table.getLocalVariables(currentMethod).contains(symbol) || table.getParameters(currentMethod).contains(symbol) || table.getFields().contains(symbol);
        }

        if(!isVirtual){
            computation.append("invokestatic(%s, \"%s\"%s)".formatted(caller.get("name"), methodName, argsCode)); // possivel repeticao nome import
        }
        else{
            computation.append("invokevirtual(%s, \"%s\"%s)".formatted(callerRes.getCode(), methodName, argsCode));
        }

        computation.append(type);
        computation.append(";\n");

        return new OllirExprResult(code,computation);
    }

    private OllirExprResult visitNewClass(JmmNode node, Void unused){
        StringBuilder computation = new StringBuilder();

        var className = node.get("name");

        var temp = ollirTypes.nextTemp();
        String code = "%s.%s".formatted(temp,className);
        computation.append("%s :=.%s new(%s).%s;\n".formatted(code,className,className,className));
        computation.append("invokespecial(%s, \"<init>\").V;\n".formatted(code));

        return new OllirExprResult(code,computation);
    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused){
        var expr = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());
        var type = ".array.i32";
        String code = ollirTypes.nextTemp() + type;
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(type).append(SPACE)
                .append("new(array, %s).array.i32".formatted(expr.getCode()))
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayInit(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        var temp = ollirTypes.nextTemp();

        String code = "%s.array.i32".formatted(temp);
        computation.append("%s :=.array.i32 new(array, %s.i32).array.i32;\n".formatted(code,node.getNumChildren()));

        for (var i = 0; i < node.getNumChildren(); i++) {
            var child = visit(node.getChild(i));
            computation.append(child.getComputation());
            computation.append("%s[%s.i32].i32 :=.i32 %s;\n".formatted(temp,i,child.getCode()));
        }

        return new OllirExprResult(code, computation);    }

    // ver field
    private OllirExprResult visitArrayAcess(JmmNode node, Void unused){
        StringBuilder computation = new StringBuilder();

        var arrayName = visit(node.getChild(0));
        var acessIndex = visit(node.getChild(1));

        var temp = ollirTypes.nextTemp();
        String code = "%s.i32".formatted(temp);

        computation.append(arrayName.getComputation());
        computation.append(acessIndex.getComputation());
        computation.append("%s :=.i32 %s[%s].i32;\n".formatted(code,arrayName.getCode(),acessIndex.getCode()));

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayLength(JmmNode node, Void unused){
        StringBuilder computation = new StringBuilder();

        var expr = visit(node.getChild(0));

        var temp = ollirTypes.nextTemp();
        String code = "%s.i32".formatted(temp);

        computation.append(expr.getComputation());
        computation.append("%s :=.i32 arraylength(%s).i32;\n".formatted(code,expr.getCode()));

        return new OllirExprResult(code,computation);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        if(node.get("op").equals("&&")){
            return visitShortCircuit(node,unused);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node,currentMethod);
        String resOllirType = ollirTypes.toOllirType(resType);

        // Check if this is a direct assignment context
        if (isDirectAssignment) {
            // Return the computation and the operation directly without temp variable
            String operationCode = lhs.getCode() + SPACE + node.get("op") + resOllirType + SPACE + rhs.getCode();
            return new OllirExprResult(operationCode, computation);
        } else {
            // Use temporary variable for complex expressions
            String code = ollirTypes.nextTemp() + resOllirType;
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(lhs.getCode()).append(SPACE);

            Type type = types.getExprType(node,currentMethod);
            computation.append(node.get("op")).append(ollirTypes.toOllirType(type)).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);

            return new OllirExprResult(code, computation);
        }
    }

    private OllirExprResult visitShortCircuit(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        var thenLabel = ollirTypes.nextThenLabel();
        var endLabel = ollirTypes.nextEndLabel();
        var code = ollirTypes.nextAndLabel() + ".bool";
        computation.append(lhs.getComputation());
        computation.append("if (%s) goto %s;\n".formatted(lhs.getCode(),thenLabel));
        computation.append("%s :=.bool 0.bool;\n".formatted(code));
        computation.append("goto %s;\n".formatted(endLabel));
        computation.append("%s:\n".formatted(thenLabel));
        computation.append(rhs.getComputation());
        computation.append("%s :=.bool %s;\n".formatted(code, rhs.getCode()));
        computation.append("%s:\n".formatted(endLabel));

        return new OllirExprResult(code,computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        Type type = types.getExprType(node,currentMethod);
        String ollirType = ollirTypes.toOllirType(type);
        var id = node.get("name");

        String code = id + ollirType;

        Symbol symbol = new Symbol(type,id);
        if (!table.getLocalVariables(currentMethod).contains(symbol) && !table.getParameters(currentMethod).contains(symbol) && table.getFields().contains(symbol)){
            var result = visitVarRefField(node,unused);
            computation.append(result.getComputation());
            code = result.getCode();
        }

        return new OllirExprResult(code, computation);
    }
    // handles getfield
    private OllirExprResult visitVarRefField(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        Type type = types.getExprType(node,currentMethod);
        String ollirType = ollirTypes.toOllirType(type);
        var id = node.get("name");

        String code = ollirTypes.nextTemp() + ollirType;

        computation.append("%s :=%s getfield(this, %s%s)%s;\n".formatted(code,ollirType,id,ollirType,ollirType));

        return new OllirExprResult(code, computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}