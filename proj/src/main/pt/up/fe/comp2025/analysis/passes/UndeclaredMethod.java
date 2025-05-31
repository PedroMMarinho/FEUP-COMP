package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitorReverse;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class UndeclaredMethod extends AnalysisVisitorReverse {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_CALL,this::visitMethodCall);

    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table){
        var typeUtils = new TypeUtils(table);
        var currentMethod = methodCall.getAncestor(Kind.METHOD_DECL).orElse(null);

        assert currentMethod != null; // i think this is always not null but just in case
        if(!typeUtils.methodExists(methodCall,currentMethod.get("name"))){
            var message = String.format("Method '%s' does not exist.", methodCall.get("name"));
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodCall.getLine(),
                    methodCall.getColumn(),
                    message,
                    null)
            );
        }
        return null;
    }





}
