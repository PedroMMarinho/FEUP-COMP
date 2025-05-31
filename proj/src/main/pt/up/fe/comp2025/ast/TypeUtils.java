package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.METHOD_DECL;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static Type newIntType() {
        var type = new Type("int", false);
        type.putObject("isVararg", false);
        return type;
    }

    public static Type newVoidType() {
        var type = new Type("void", false);
        type.putObject("isVararg", false);
        return type;
    }

    public static Type newBooleanType() {
        var type = new Type("boolean", false);
        type.putObject("isVararg", false);
        return type;
    }

    public static Type newSingleObject(String objectType){
        var type = new Type(objectType,false);
        type.putObject("isVararg", false);
        return type;
    }

    public static Type newStringArrayType(){
        var type = new Type("String",true);
        type.putObject("isVararg", false);
        return type;
    }

    public static Type newIntArrayType(){
        var type = new Type("int",true);
        type.putObject("isVararg", false);
        return type;
    }

    public static Type convertType(JmmNode typeNode) {
        var name = typeNode.get("name");
        var isArray = Boolean.parseBoolean(typeNode.get("isArray"));
        var isVararg = typeNode.get("isVararg");

        var type = new Type(name,isArray);
        type.putObject("isVararg", isVararg);

        return type;
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr,String currentMethod) {
        if( expr.getKind().equals("BinaryExpr")){
            var operator = expr.get("op");
            if(operator.equals("+") || operator.equals("-") || operator.equals("/") || operator.equals("*") ) {
                return newIntType();
            }
            if( operator.equals(">") || operator.equals("<") || operator.equals("&&")){
                return newBooleanType();
            }

        }
        if( expr.getKind().equals("VarRefExpr")){
            return getVarRefType(expr.get("name"), currentMethod);

        }
        if (expr.getKind().equals("Paren")) {
            return getExprType(expr.getChild(0), currentMethod);
        }
        if( expr.getKind().equals("IntegerLiteral") || expr.getKind().equals("ArrayLength")){
            return newIntType();
        }
        if( expr.getKind().equals("This")){
            return newSingleObject(table.getClassName());
        }
        if( expr.getKind().equals("BooleanLiteral")){
            return newBooleanType();
        }
        if(expr.getKind().equals("Not")){
            return newBooleanType();
        }
        if(expr.getKind().equals("ArrayInit") || expr.getKind().equals("NewArray")){
            return new Type("int",true);
        }
        if( expr.getKind().equals("ArrayAccess")){
            return newSingleObject(getExprType(expr.getChild(0),currentMethod).getName());
        }
        if(expr.getKind().equals("MethodCall")){
            return table.getReturnType(expr.get("name"));
        }
        if(expr.getKind().equals("NewClass")){
            return newSingleObject(expr.get("name"));
        }

        return null;


    }

    public Type getVarRefType(String varRef, String currentMethod) {
        if(currentMethod != null){
            for( var symbol: table.getLocalVariables(currentMethod)){
                if( symbol.getName().equals(varRef)){
                    return symbol.getType();
                }
            }
            for( var symbol: table.getParameters(currentMethod)){
                if( symbol.getName().equals(varRef)){
                    return symbol.getType();
                }
            }
        }
        for( var symbol: table.getFields()){
            if( symbol.getName().equals(varRef)){
                return symbol.getType();
            }
        }
        if(table.getImports().stream().map(TypeUtils::shortenImport).toList().contains(varRef)){
            return newSingleObject(varRef);
        }
        return null;
    }

    public Boolean methodExists(JmmNode methodCall,String currentMethod){
        var object = methodCall.getChild(0);
        var method = methodCall.get("name");
        var objectType = getExprType(object,currentMethod);
        return isImportedOrSuper(objectType) ||
                belongsToMainClass(methodCall,currentMethod); // method from current class
    }

    public boolean belongsToMainClass(JmmNode methodCall,String currentMethod) {
        var object = methodCall.getChild(0);
        var method = methodCall.get("name");
        var objectType = getExprType(object,currentMethod);
        return table.getMethods().contains(method) && objectType.getName().equals(table.getClassName());
    }

    public boolean isImportedOrSuper(Type objectType) {
        return (objectType.getName().equals(table.getClassName()) && table.getSuper() != null) || // object extends a class and call method from it
                table.getImports().stream().map(TypeUtils::shortenImport).toList().contains(objectType.getName()); // imported class
    }

    public static boolean isVararg(Type objectType){
        return Boolean.parseBoolean(objectType.getObject("isVararg").toString());
    }

    public boolean verifyTypeCompatibility(Type assignType, Type assigneeType){
        boolean sameType = assignType.equals(assigneeType);
        boolean extendsCurrentClass = assignType.getName().equals(table.getClassName()) && assigneeType.getName().equals(table.getSuper());
        boolean doubleImport = table.getImports().stream().map(TypeUtils::shortenImport).toList().contains(assignType.getName()) && table.getImports().contains(assigneeType.getName());

        return sameType || extendsCurrentClass || doubleImport;
    }

    public static String shortenImport(String importString){
        return importString.substring(importString.lastIndexOf('.')+1);
    }

    public boolean acessFieldInStaticMethod (JmmNode node, String currentMethod) {
        if (node.getKind().equals("VarRefExpr")) {
            String varName = node.get("name");
            Symbol symbol = new Symbol(getVarRefType(varName, currentMethod), varName);
            JmmNode method = node.getAncestor(METHOD_DECL).orElse(null);
            assert method != null; // shouldnt ever happen
            var isStatic = Boolean.parseBoolean(method.get("isStatic"));

            if (!table.getLocalVariables(currentMethod).contains(symbol) && table.getFields().contains(symbol) && isStatic  ) {
                return true;
            }
        }
        return false;
    }

}
