package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;


public class VarargOpt {
    private JmmSemanticsResult semanticsResult;


    public VarargOpt(JmmSemanticsResult semanticsResult) {
        this.semanticsResult = semanticsResult;
    }

    public void optimize() {
        var root = semanticsResult.getRootNode();

        for (JmmNode methodNode : root.getDescendants(METHOD_CALL))
            visitMethodCall(methodNode, semanticsResult.getSymbolTable());
    }


    public void visitMethodCall(JmmNode node, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);

        // get current method
        var parent = node.getParent();
        while(!parent.getKind().equals(METHOD_DECL.toString())){
            parent = parent.getParent();
        }
        var currentMethod = parent.get("name");

        // call to imported or extended -- does not know if it has vararg params
        var nodeType = typeUtils.getExprType(node.getChild(0), currentMethod);
        if(!nodeType.getName().equals(table.getClassName())){
            return;
        }

        var methodCallName = node.get("name");
        var args = node.getChildren().subList(1,node.getNumChildren());
        var params = table.getParameters(methodCallName);

        //method without varargs
        if(params.isEmpty() || !TypeUtils.isVararg(params.getLast().getType())) return;

        // last arg already array
        if(!(args.size() < params.size()) && args.getLast().getKind().equals(ARRAY_INIT.toString())) return;

        JmmNode arrayNode = new JmmNodeImpl(List.of("ArrayInit", "Expr"));

        // Last arguments are for VarArg
        for (int i = params.size()-1; i<args.size(); i++)
        {
            JmmNode argNode = args.get(i);
            node.removeChild(argNode);
            arrayNode.add(argNode);
            argNode.setParent(arrayNode);
        }

        node.add(arrayNode);
        arrayNode.setParent(node);
    }
}
