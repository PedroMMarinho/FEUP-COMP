package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {

        reports = new ArrayList<>();

        var imports = buildImports(root);
        JmmNode classDecl = root.getChildren().get(root.getNumChildren() - 1 );

        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");
        String superName = classDecl.getOptional("superName").orElse(null);
        var test = imports.stream().map(TypeUtils::shortenImport).toList();
        if(superName != null && !test.contains(superName)){
            var message = String.format("Super class '%s' not imported", superName);
            reports.add(newError(classDecl,message));
        }
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var fields = buildFields(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals,fields,imports,superName);
    }


    private List<String> buildImports(JmmNode program){
        List<String> imports = new ArrayList<>() ;
        for( JmmNode child: program.getChildren() ){
            if( !Kind.CLASS_DECL.check(child)   ){
                var packagePath = child.get("packageName").replaceAll("[\\[\\] ]", "").replaceAll(",", ".");

                var shortenedImports = imports.stream().map(TypeUtils::shortenImport).toList();
                if( shortenedImports.contains(TypeUtils.shortenImport(packagePath))){
                    reports.add(newError(child, "Duplicated import declarations"));
                }
                else {
                    imports.add(packagePath);
                }

            }

        }
        return imports;
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (var varDecl: classDecl.getChildren(VAR_DECL)) {

            boolean noErrors = true;
            for( var  field: fields ){
                if( field.getName().equals(varDecl.get("name"))){
                    noErrors = false;
                    break;
                }
            }

            if(noErrors){
                var fieldType = TypeUtils.convertType(varDecl.getChild(0));
                if(TypeUtils.isVararg(fieldType)){
                    reports.add(newError(varDecl,"Field type cannot be vararg"));
                }
                fields.add( new Symbol(fieldType,varDecl.get("name")));
            }
            else {
                reports.add( newError(varDecl,"Duplicated fields"));

            }

        }
        return fields;
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var methodName = method.get("name");

            if(methodName.equals("main")){
                map.put(methodName, TypeUtils.newVoidType());
            }else{
                var type = TypeUtils.convertType(method.getChild(0));
                if(TypeUtils.isVararg(type)){
                    reports.add(newError(method,"Return type cannot be vararg"));
                    return null;
                }
                map.put(methodName, type);
            }
        }

        return map;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            List<Symbol> paramList = new ArrayList<>();
            var varargFound = false;

            if(name.equals("main")){
                var symbol = new Symbol(TypeUtils.newStringArrayType(), method.get("args"));
                paramList.add(symbol);
                map.put(name, paramList);
                continue;
            }

            for (var param : method.getChildren(PARAM)){
                var symbol = new Symbol(TypeUtils.convertType(param.getChild(0)), param.get("name"));
                var vararg = TypeUtils.isVararg(symbol.getType());

                if (varargFound){
                    if (vararg){
                        reports.add(newError(param,"Cannot have more than one vararg parameter"));
                    }
                    reports.add(newError(param,"Vararg parameter must be the last one"));
                }
                else{
                    if(vararg){
                        varargFound = true;
                    }
                }


                if (paramList.contains(symbol)){
                    reports.add(newError(param,"Duplicate Parameter"));
                }
                else{
                    paramList.add(symbol);
                }
            }
            map.put(name, paramList);
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            var name = method.get("name");
            List<Symbol> localsList = new ArrayList<>();
            for (var varDecl : method.getChildren(VAR_DECL)){
                var varType =  TypeUtils.convertType(varDecl.getChild(0));
                if(TypeUtils.isVararg(varType)){
                    reports.add(newError(method,"Variable type cannot be vararg"));
                }
                var symbol = new Symbol(varType, varDecl.get("name"));
                if (localsList.contains(symbol)){
                    reports.add(newError(varDecl,"Duplicate Local Variable"));
                }
                else{
                    localsList.add(symbol);
                }
            }
            map.put(name, localsList);

        }

        return map;


    }

    private List<String> buildMethods(JmmNode classDecl) {


        var methods = new ArrayList<String>();

        for(var method: classDecl.getChildren(METHOD_DECL)){
            String newMethod = method.get("name");
            if(methods.contains(newMethod)){
                reports.add(newError(method, "Duplicated methods"));
            }
            else{
                methods.add(newMethod);
            }
        }

        return methods;
    }


}
