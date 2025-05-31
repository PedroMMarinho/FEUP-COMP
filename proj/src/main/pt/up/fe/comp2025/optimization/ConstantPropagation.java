package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

public class ConstantPropagation extends AJmmVisitor<SymbolTable,Set<String>> {

    private Map<String, JmmNode> constants;

    private String currentMethod;

    private boolean changed;



    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL,this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT,this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRef);
        addVisit(Kind.WHILE_STMT,this::visitWhileStmt);
        addVisit(Kind.IF_STMT,this::visitIfStmt);
        addVisit(Kind.SCOPE_STMT, this::visitScopeStmt);
        this.setDefaultVisit(this::defaultVisit);

    }

    private Set<String> visitIfStmt( JmmNode jmmNode, SymbolTable table){
        var cond = jmmNode.getChild(0);
        var thenBody = jmmNode.getChild(1);
        var elseBody = jmmNode.getChild(2);
        var constantsCopy = new HashMap<String,JmmNode>(constants);
        visit(cond,table);
        var assignmentsDone = new HashSet<String>(visit(thenBody,table));
        constants = new HashMap<>(constantsCopy);
        assignmentsDone.addAll(visit(elseBody,table));
        constants = new HashMap<>(constantsCopy);
        for( var reference: assignmentsDone){
            constants.remove(reference);
        }
        return null;
    }

    private Set<String> visitScopeStmt(JmmNode jmmNode, SymbolTable table){
        var assignmentsDone = new HashSet<String>();
        for( var node : jmmNode.getChildren()){
            var result = visit(node,table);
            if( result != null){
                assignmentsDone.addAll(result);
            }
        }
        return assignmentsDone;
    }


    private Set<String> visitWhileStmt( JmmNode jmmNode, SymbolTable table){ // could be wrong ( maybe its just descendants )
        var cond = jmmNode.getChild(0);
        var body = jmmNode.getChild(1);
        for( var node : jmmNode.getDescendants(Kind.ASSIGN_STMT)){
            constants.remove(node.getChild(0).get("name"));
        }
        visit(cond,table);
        visit(body,table);
        return null;
    }


    private Set<String> visitAssignStmt(JmmNode jmmNode, SymbolTable table) {
        var assign = jmmNode.getChild(1);
        var assignee = jmmNode.getChild(0);
        if(Kind.BOOLEAN_LITERAL.check(assign) || Kind.INTEGER_LITERAL.check(assign)){

            if( isLocal(table, assignee.get("name"))){
                constants.put(assignee.get("name"),assign);
            }
        }
        else{
            constants.remove(assignee.get("name"));
        }
        visit(assign,table);
        visit(assignee,table);


        return Set.of(assignee.get("name"));


    }

    private Set<String> visitVarRef(JmmNode jmmNode, SymbolTable table) {
        var parent = jmmNode.getParent();
        if(Kind.ASSIGN_STMT.check(parent) && parent.getChild(0).equals(jmmNode)) return null;
        if( isLocal(table, jmmNode.get("name"))){
            var node = constants.get(jmmNode.get("name"));
            if(node != null){
                var nodeCopy = node.copy(node.getHierarchy());
                for(String attr : node.getAttributes()) {
                    nodeCopy.put(attr, node.get(attr));
                }
                jmmNode.replace( nodeCopy);
                changed = true;
            }
        }
        return null;
    }



    private Set<String> visitMethodDecl(JmmNode jmmNode, SymbolTable table) {
        constants = new HashMap<>();
        currentMethod = jmmNode.get("name");
        for( var statement: jmmNode.getChildren(Kind.STMT)){
            visit(statement,table);
        }
        for( var statement : jmmNode.getChildren(Kind.RETURN_STMT)){
            visit(statement,table);
        }
        return null;
    }


    private boolean isLocal(SymbolTable table, String varName ){
        for(var symbol : table.getLocalVariables(currentMethod)){
            if(varName.equals(symbol.getName())){
                return true;
            }
        }
        for(var symbol: table.getParameters(currentMethod)){
            if(varName.equals(symbol.getName())){
                return true;
            }
        }
        return false;
    }

    private Set<String> defaultVisit(JmmNode jmmNode, SymbolTable table){
        for (var node : jmmNode.getChildren()){
            visit(node,table);
        }
        return null;
    }

    public boolean isChanged() {
        return changed;
    }

    public void reset() {
        this.changed = false;
    }
}

