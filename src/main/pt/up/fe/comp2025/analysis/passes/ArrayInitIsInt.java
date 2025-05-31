package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ArrayInitIsInt extends AnalysisVisitor {
    @Override
    protected void buildVisitor() {
        addVisit(Kind.ARRAY_INIT,this::visitArrayInit);
    }

    public Void visitArrayInit(JmmNode node, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);
        for (var child : node.getChildren()){
            if (!typeUtils.getExprType(child,"value").equals(TypeUtils.newIntType())){
                addReport(
                    Report.newError(Stage.SEMANTIC,
                            node.getLine(),
                            node.getColumn(),
                            "Array element must be type int",
                            null)
                );
            }
        }
        return null;
    }

}
