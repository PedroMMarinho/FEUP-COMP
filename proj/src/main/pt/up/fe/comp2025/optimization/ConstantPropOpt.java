package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;

public class ConstantPropOpt {

    JmmSemanticsResult semanticsResult;

    ConstantPropOpt(JmmSemanticsResult semanticsResult){
        this.semanticsResult = semanticsResult;
    }

    public void optimize() {
        var constantProp = new ConstantPropagation();
        var constantFold = new ConstantFolding();
        var changeProp = false;
        var changeFold = false;
        do{
            constantProp.reset();
            constantFold.reset();
            constantProp.visit(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
            changeProp = constantProp.isChanged();

            constantFold.analyze(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
            changeFold = constantFold.isChanged();

        }while(changeProp || changeFold);

    }
}
