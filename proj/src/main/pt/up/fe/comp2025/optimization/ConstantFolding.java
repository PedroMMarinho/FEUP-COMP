package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.analysis.AnalysisVisitorReverse;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;

public class ConstantFolding extends AnalysisVisitorReverse {

    private boolean changed;


    public boolean isChanged() {
        return changed;
    }

    public void reset() {
        this.changed = false;
    }
    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitBinaryExpr(JmmNode jmmNode, SymbolTable table) {
        var left = jmmNode.getChild(0);
        var right = jmmNode.getChild(1);
        if(Kind.INTEGER_LITERAL.check(left) && Kind.INTEGER_LITERAL.check(right)){
            Kind kind;
            String result;
            var valLeft = Integer.parseInt(left.get("value"));
            var valRight =Integer.parseInt(right.get("value"));
            var op = jmmNode.get("op");
             switch (op) {
                case "+":
                    result = String.valueOf(valLeft + valRight);
                    kind = Kind.INTEGER_LITERAL;
                    break;

                case "-":
                    result =String.valueOf(valLeft - valRight);
                    kind = Kind.INTEGER_LITERAL;
                    break;
                case "*" :
                    result =String.valueOf(valLeft * valRight);
                    kind = Kind.INTEGER_LITERAL;
                    break;
                case "/" :
                    result = String.valueOf(valLeft / valRight);
                    kind = Kind.INTEGER_LITERAL;
                    break;
                case ">" :
                    result = String.valueOf(valLeft > valRight);
                    kind = Kind.BOOLEAN_LITERAL;
                    break;
                case "<" :
                    result = String.valueOf(valLeft < valRight);
                    kind = Kind.BOOLEAN_LITERAL;
                    break;
                 default:
                     result = "";
                     kind = Kind.INTEGER_LITERAL;
            };
            var newNode = new JmmNodeImpl(List.of( kind.toString(), Kind.EXPR.toString() ));
            newNode.put("value", result);
            jmmNode.replace(newNode);
        }
        if(Kind.BOOLEAN_LITERAL.check(left) && Kind.BOOLEAN_LITERAL.check(right)){
            var valLeft = Boolean.parseBoolean(left.get("value"));
            var valRight =Boolean.parseBoolean(right.get("value"));
            var newNode = new JmmNodeImpl(List.of(Kind.BOOLEAN_LITERAL.toString(), Kind.EXPR.toString()));
            newNode.putObject("value", String.valueOf(valLeft && valRight));
            jmmNode.replace(newNode);
        }
        return null;
    }
}
