package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.ClassUnit;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.ConfigOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        // Vararg optimization
        var opt = new VarargOpt(semanticsResult);
        opt.optimize();
        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        boolean optimize = ConfigOptions.getOptimize(semanticsResult.getConfig());

        System.out.println("--- Pre propFold ---");
        System.out.println(semanticsResult.getRootNode().toTree());
        if(!optimize)  System.out.println("not optmize");
        ;
        if(!optimize) return semanticsResult;


        ConstantPropOpt opt = new ConstantPropOpt(semanticsResult);

        opt.optimize();

        System.out.println("--- Post propFold ---");
        System.out.println(semanticsResult.getRootNode().toTree());



        return semanticsResult;
    }


    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        int nRegisters = ConfigOptions.getRegisterAllocation(ollirResult.getConfig());

        System.out.println("--- Pre regalloc ---");
        System.out.println(ollirResult.getOllirCode());

        // Default Value no optimization
        if (nRegisters == -1) return ollirResult;

        RegisterAllocationOpt opt = new RegisterAllocationOpt(ollirResult.getOllirClass());
        opt.allocateReg(nRegisters);

        System.out.println("--- Post regalloc ---");
        System.out.println(ollirResult.getOllirCode());

        return ollirResult;
    }

}
