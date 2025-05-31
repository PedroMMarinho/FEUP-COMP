package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class ThisInStaticMethod extends AnalysisVisitor {
    private boolean isStatic;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.THIS, this::visitThis);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        isStatic = Boolean.parseBoolean(method.get("isStatic"));
        return null;
    }


    private Void visitThis(JmmNode thisNode, SymbolTable table){
        if(isStatic){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    thisNode.getLine(),
                    thisNode.getColumn(),
                    "Cannot use this reference in a static method",
                    null)
            );
        }
        return null;
    }




}

