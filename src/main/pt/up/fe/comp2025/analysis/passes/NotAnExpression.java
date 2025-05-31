package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class NotAnExpression extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.EXPR_STMT,this::visitExprStmt);
    }

    private Void visitExprStmt(JmmNode exprStmt, SymbolTable table){
        if( !exprStmt.getChildren(Kind.METHOD_CALL).isEmpty() ){ return null;}
        if( !exprStmt.getChildren(Kind.PAREN).isEmpty()){
            visitExprStmt(exprStmt.getChild(0),table);
            return null;
        }
        addReport(Report.newError(
                Stage.SEMANTIC,
                exprStmt.getLine(),
                exprStmt.getColumn(),
                "Not a statement",
                null)
        );
        return null;
    }


}
