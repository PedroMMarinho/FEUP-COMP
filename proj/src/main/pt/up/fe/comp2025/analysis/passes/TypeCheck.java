package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class TypeCheck extends AnalysisVisitor {
    private String currentMethod;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.IF_STMT, this::visitConditions);
        addVisit(Kind.WHILE_STMT, this::visitConditions);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignment);
        addVisit(Kind.METHOD_CALL, this::visitMethodCall);
        addVisit(Kind.NOT, this::visitNot);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.ARRAY_ASSIGN_STMT, this::visitArrayAssignment);
        addVisit(Kind.ARRAY_LENGTH, this::visitArrayLength);
    }



    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {

        TypeUtils typeUtils = new TypeUtils(table);
        var operator = binaryExpr.get("op");
        var leftExpr = binaryExpr.getChild(0);
        var rightExpr = binaryExpr.getChild(1);
        Type leftType;
        Type rightType;

        if(typeUtils.acessFieldInStaticMethod(leftExpr,currentMethod) || typeUtils.acessFieldInStaticMethod(rightExpr,currentMethod)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    binaryExpr.getLine(),
                    binaryExpr.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if(operator.equals("+") || operator.equals("-") || operator.equals("/") || operator.equals("*") || operator.equals("<") || operator.equals(">"))
        {
            if(leftExpr.getKind().equals("MethodCall") && typeUtils.isImportedOrSuper(typeUtils.getExprType(leftExpr.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(leftExpr,currentMethod)){
                leftType = TypeUtils.newIntType();
            }
            else{
                leftType = typeUtils.getExprType(leftExpr,currentMethod);

            }
            if(rightExpr.getKind().equals("MethodCall") && typeUtils.isImportedOrSuper(typeUtils.getExprType(rightExpr.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(rightExpr,currentMethod)){
                rightType = TypeUtils.newIntType();
            }
            else{
                rightType = typeUtils.getExprType(rightExpr,currentMethod);

            }
            if(!leftType.equals(TypeUtils.newIntType()) || !rightType.equals(TypeUtils.newIntType()) ){
                var message = String.format("Expected 'int' types. Got: '%s' and '%s'",leftType.print(),rightType.print());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        binaryExpr.getLine(),
                        binaryExpr.getColumn(),
                        message,
                        null)
                );
                return null;
            }
        }
        else{
            if(leftExpr.getKind().equals("MethodCall") && typeUtils.isImportedOrSuper(typeUtils.getExprType(leftExpr.getChild(0),currentMethod))){
                leftType = TypeUtils.newBooleanType();
            }
            else{
                leftType = typeUtils.getExprType(leftExpr,currentMethod);

            }
            if(leftExpr.getKind().equals("MethodCall") && typeUtils.isImportedOrSuper(typeUtils.getExprType(rightExpr.getChild(0),currentMethod))){
                rightType = TypeUtils.newBooleanType();
            }
            else{
                rightType = typeUtils.getExprType(rightExpr,currentMethod);

            }
            if(!leftType.equals(TypeUtils.newBooleanType()) || !rightType.equals(TypeUtils.newBooleanType())){
                var message = String.format("Expected 'boolean' types. Got: '%s' and '%s'",leftType.print(),rightType.print());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        binaryExpr.getLine(),
                        binaryExpr.getColumn(),
                        message,
                        null)
                );
                return null;
            }
        }
        return null;
    }

    private Void visitArrayAccess( JmmNode arrayAccess, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        var leftSide = arrayAccess.getChild(0);
        var rightSide = arrayAccess.getChild(1);
        var leftType = typeUtils.getExprType(leftSide,currentMethod);
        var rightType = typeUtils.getExprType(rightSide,currentMethod);
        var leftSizeImported = leftSide.getKind().equals(Kind.METHOD_CALL.toString()) && typeUtils.isImportedOrSuper(typeUtils.getExprType(leftSide.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(leftSide,currentMethod);
        var rightSizeImported = rightSide.getKind().equals(Kind.METHOD_CALL.toString()) && typeUtils.isImportedOrSuper(typeUtils.getExprType(rightSide.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(rightSide,currentMethod);

        if(typeUtils.acessFieldInStaticMethod(leftSide,currentMethod)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAccess.getLine(),
                    arrayAccess.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if(!(leftSizeImported||leftType.isArray() || TypeUtils.isVararg(leftType)) ){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAccess.getLine(),
                    arrayAccess.getColumn(),
                    "Cannot access member of array on a non array object",
                    null)

            );
            return null;
        }

        if(!( rightSizeImported||rightType.equals(TypeUtils.newIntType()))){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAccess.getLine(),
                    arrayAccess.getColumn(),
                    "Cannot access array with non integer indexes",
                    null)

            );
            return null;
        }
        return null;

    }

    private Void visitConditions(JmmNode cond, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        var condition = cond.getChild(0);
        var condType = typeUtils.getExprType(condition, currentMethod);
        var condTypeImported = condition.getKind().equals(Kind.METHOD_CALL.toString()) && typeUtils.isImportedOrSuper(typeUtils.getExprType(condition.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(condition,currentMethod);

        if(typeUtils.acessFieldInStaticMethod(cond,currentMethod)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    cond.getLine(),
                    cond.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if( !(condTypeImported ||condType.equals(TypeUtils.newBooleanType()))){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    cond.getLine(),
                    cond.getColumn(),
                    "Cannot use types different than boolean in conditions",
                    null)

            );
            return null;
        }
        return null;
    }

    private Void visitAssignment(JmmNode assign, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        var leftSide = assign.getChild(0);
        var rightSide = assign.getChild(1);
        var leftType = typeUtils.getExprType(leftSide,currentMethod);
        var rightType = typeUtils.getExprType(rightSide,currentMethod);

        if(typeUtils.acessFieldInStaticMethod(leftSide,currentMethod) || typeUtils.acessFieldInStaticMethod(rightSide,currentMethod)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assign.getLine(),
                    assign.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if (leftSide.getKind().equals("VarRefExpr")){
            if(Kind.METHOD_CALL.check(rightSide)){
                var type = typeUtils.getExprType(rightSide.getChild(0), currentMethod);
                if (typeUtils.isImportedOrSuper(type)){
                    return null;
                }
            }

            if(!typeUtils.verifyTypeCompatibility(rightType,leftType)){
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        assign.getLine(),
                        assign.getColumn(),
                        "Assigned has different type from assignee",
                        null)
                );
            }
            return null;
        }
        else{
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assign.getLine(),
                    assign.getColumn(),
                    "Assignee must be a variable",
                    null)
            );
        }
        return null;

    }

    private Void visitMethodCall(JmmNode call, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        // Verify if var call is imported or super if it is skip all
        var callType = typeUtils.getExprType(call.getChild(0), currentMethod);
        var callArgs = call.getChildren().subList(1,call.getChildren().size());
        if(!typeUtils.isImportedOrSuper(callType) || typeUtils.belongsToMainClass(call,currentMethod)){
            var methodCall = call.get("name");
            var params = table.getParameters(methodCall);

            if(params.isEmpty() && callArgs.isEmpty()) {
                return null;
            }
            var idx = 0;
            var arrayFound = false;
            var atrrFound = false;
            for (var callArg : callArgs){
                if (arrayFound){
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            call.getLine(),
                            call.getColumn(),
                            "Cannot have arguments after an array when param is vararg",
                            null)
                    );
                    return null;
                }
                if (idx >= params.size()){ // Received more than expected
                    var message = String.format("Method '%s' expected '%d' arguments. Got: '%d'",call.get("name"),params.size(),callArgs.size());
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            call.getLine(),
                            call.getColumn(),
                            message,
                            null)
                    );
                    return null;
                }
                var paramType = params.get(idx).getType();

                if(callArg.getKind().equals("MethodCall")){
                    var objType = typeUtils.getExprType(callArg.getChild(0),currentMethod);
                    if(typeUtils.isImportedOrSuper(objType) && !typeUtils.belongsToMainClass(callArg,currentMethod)){
                        if(!TypeUtils.isVararg(paramType)){
                            idx++;
                        }
                        continue; }
                }
                var callArgType = typeUtils.getExprType(callArg,currentMethod);

                if(TypeUtils.isVararg(paramType)){
                    if(!callArgType.getName().equals(paramType.getName())){
                        var message = String.format("Method '%s' parameter type mismatch. Expected '%s', got: '%s'",call.get("name"),paramType.getName(),callArgType.getName());
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                call.getLine(),
                                call.getColumn(),
                                message,
                                null)
                        );
                        return null;
                    }
                    if(callArgType.isArray() && callArgType.getName().equals(paramType.getName())){
                        if (atrrFound){
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    call.getLine(),
                                    call.getColumn(),
                                    "Cannot have an array after an attribute when param is vararg",
                                    null)
                            );
                            return null;
                        }
                        arrayFound = true;
                        continue;
                    }
                    else{
                        atrrFound = true;
                    }
                }



                if(!TypeUtils.isVararg(paramType) && !typeUtils.verifyTypeCompatibility(callArgType,paramType)){ // Mismatch type
                    var message = String.format("Method '%s' parameter type mismatch. Expected '%s', got: '%s'",call.get("name"),paramType.getName(),callArgType.getName());
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            call.getLine(),
                            call.getColumn(),
                            message,
                            null)
                    );
                    return null;
                }

                if(!TypeUtils.isVararg(paramType)){
                    idx++;
                }

            }
            if (!( idx == params.size()  || TypeUtils.isVararg(params.get(idx).getType()) && idx == params.size()-1)) { // Received less than expected
                var message = String.format("Method '%s' expected '%d' arguments. Got: '%d'", call.get("name"), params.size(), callArgs.size());

                if (TypeUtils.isVararg(params.get(idx).getType())) {
                    message = String.format("Method '%s' expected at least '%d' arguments(last is vararg). Got: '%d'", call.get("name"), params.size(), callArgs.size());
                }
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        call.getLine(),
                        call.getColumn(),
                        message,
                        null)
                );
                return null;
            }

        }
        else{
            for (var callArg : callArgs){
                if(typeUtils.acessFieldInStaticMethod(callArg,currentMethod)){
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        call.getLine(),
                        call.getColumn(),
                        "Cannot access fields in a static method ",
                        null)
                );
                    return null;
                }
            }
        }
        return null;

    }

    private Void visitNot(JmmNode not, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        var expr = not.getChild(0);
        var exprType = typeUtils.getExprType(expr,currentMethod);
        var condTypeImported =  expr.getKind().equals(Kind.METHOD_CALL.toString()) && typeUtils.isImportedOrSuper(typeUtils.getExprType(expr.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(expr,currentMethod);

        if(typeUtils.acessFieldInStaticMethod(expr,currentMethod)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    not.getLine(),
                    not.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if (!(condTypeImported || exprType.equals(TypeUtils.newBooleanType()))){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    not.getLine(),
                    not.getColumn(),
                    "Expression must be a boolean",
                    null)
            );
        }
        return null;
    }

    private Void visitReturnStmt(JmmNode ret, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        var expr = ret.getChild(0);
        var retType = typeUtils.getExprType(expr,currentMethod);
        if(expr.getKind().equals(Kind.METHOD_CALL.toString())) {
            var objType = typeUtils.getExprType(expr.getChild(0), currentMethod);
            if (typeUtils.isImportedOrSuper(objType) && !typeUtils.belongsToMainClass(expr,currentMethod)) {
                return null;
            }
        }
        var methodRetType = table.getReturnType(currentMethod);

        if (!typeUtils.verifyTypeCompatibility(retType, methodRetType)) {
            var message = String.format("Method '%s' expected return type '%s' . Got: '%s'", currentMethod, methodRetType, retType);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    ret.getLine(),
                    ret.getColumn(),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitArrayAssignment(JmmNode arrayAssignment, SymbolTable table) {
        var typeUtils = new TypeUtils(table);
        var arrayName = arrayAssignment.get("name");
        var arrayType = typeUtils.getVarRefType(arrayName,currentMethod);
        var index = arrayAssignment.getChild(0);
        var assign = arrayAssignment.getChild(1);

        Symbol symbol = new Symbol(arrayType,arrayName);
        if((!table.getLocalVariables(currentMethod).contains(symbol) && table.getFields().contains(symbol)) || (typeUtils.acessFieldInStaticMethod(assign,currentMethod))){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAssignment.getLine(),
                    arrayAssignment.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if(!typeUtils.getExprType(index,currentMethod).equals(TypeUtils.newIntType())){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAssignment.getLine(),
                    arrayAssignment.getColumn(),
                    "Cannot access array with non integer indexes",
                    null)
            );
            return null;
        }
        if(!arrayType.isArray()){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAssignment.getLine(),
                    arrayAssignment.getColumn(),
                    "Cannot access member of array on a non array object",
                    null)
            );
            return null;
        }
        var singleObject = TypeUtils.newSingleObject(arrayType.getName());
        if(!typeUtils.verifyTypeCompatibility(typeUtils.getExprType(assign,currentMethod),singleObject)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayAssignment.getLine(),
                    arrayAssignment.getColumn(),
                    "Assigned has different type from assignee",
                    null)
            );
            return null;
        }
        return null;


    }

    private Void visitArrayLength(JmmNode jmmNode, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);
        var object = jmmNode.getChild(0);
        var objectType = typeUtils.getExprType(object,currentMethod);
        var leftSizeImported = object.getKind().equals(Kind.METHOD_CALL.toString()) && typeUtils.isImportedOrSuper(typeUtils.getExprType(object.getChild(0),currentMethod)) && !typeUtils.belongsToMainClass(object,currentMethod);

        if(typeUtils.acessFieldInStaticMethod(object,currentMethod)){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    jmmNode.getLine(),
                    jmmNode.getColumn(),
                    "Cannot access fields in a static method ",
                    null)
            );
            return null;
        }

        if(!(leftSizeImported|| objectType.isArray() || TypeUtils.isVararg(objectType)) ){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    jmmNode.getLine(),
                    jmmNode.getColumn(),
                    "Cannot use .length on a non array object",
                    null)

            );
            return null;
        }

        return null;

    }



}
