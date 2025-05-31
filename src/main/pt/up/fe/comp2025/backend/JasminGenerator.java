package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.ArrayType;
import org.specs.comp.ollir.type.BuiltinType;
import org.specs.comp.ollir.type.ClassType;
import org.specs.comp.ollir.type.Type;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.treenode.transform.transformations.DeleteTransform;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    String className;

    private final JasminUtils jasminUtils;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        jasminUtils = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(FieldInstruction.class, this::generateFieldInstruction);
        generators.put(CallInstruction.class, this::generateCallInstruction);
        generators.put(CondBranchInstruction.class,  this::generateCondBranchInstruction);
        generators.put(GotoInstruction.class, this::generateGotoInstruction);
        generators.put(UnaryOpInstruction.class,this::generateUnaryOpInstruction);
    }

    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        var fullSuperClass = classUnit.getSuperClass();

        if(fullSuperClass == null){
            fullSuperClass = "java/lang/Object";
        }
        code.append(".super ").append(fullSuperClass).append(NL);

        for( var field: classUnit.getFields()){
            code.append(".field ")
                    .append(jasminUtils.getModifier(field.getFieldAccessModifier()))
                    .append(" '").append(field.getFieldName()).append("' ")
                    .append(JasminUtils.convertType(field.getFieldType()))
                    .append(NL);
        }


        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {
        //System.out.println("STARTING METHOD " + method.getMethodName());
        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = jasminUtils.getModifier(method.getMethodAccessModifier());

        var staticMod = method.isStaticMethod() ? " static " : "";

        var methodName = method.getMethodName();

        StringBuilder params = new StringBuilder();
        for ( var param: method.getParams() ){
            params.append(JasminUtils.convertType(param.getType()));
        }
        var returnType =  JasminUtils.convertType(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(staticMod)
                .append(methodName)
                .append("(" + params + ")" + returnType).append(NL);




        StringBuilder instructions = new StringBuilder();
        for (var inst : method.getInstructions()) {
            // Check for labels associated with this instruction
            var labels = method.getLabels(inst);

            // Add labels if they exist
            if (!labels.isEmpty()) {
                for (var label : labels) {
                    instructions.append(label).append(":").append(NL);
                }
            }

            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            instructions.append(instCode);
        }

        // Add limits
        code.append(TAB).append(".limit stack ").append(jasminUtils.getMaxStackCounter()).append(NL); // TODO: confirm later

        int localsLimit = 0;
        for( var entry: method.getVarTable().values()){
            localsLimit = Math.max(entry.getVirtualReg() + 1,localsLimit);
        }
        code.append(TAB).append(".limit locals ").append(localsLimit).append(NL);

        code.append(instructions);

        code.append(".end method\n");

        // unset method
        currentMethod = null;
        //System.out.println("ENDING METHOD " + method.getMethodName());
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // store value in the stack in destination
        var lhs = assign.getDest();
        var rhs = assign.getRhs();


        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var assignOperand = (Operand) lhs;
        var reg = currentMethod.getVarTable().get(assignOperand.getName());

        if(lhs instanceof ArrayOperand){
            code.append(apply(lhs));
        }

        if(rhs.getInstType().equals(InstructionType.BINARYOPER)){
            var binaryOp = (BinaryOpInstruction) rhs;
            if(binaryOp.getOperation().getOpType().equals(OperationType.ADD) || binaryOp.getOperation().getOpType().equals(OperationType.SUB)){
                var left = binaryOp.getLeftOperand();
                var right = binaryOp.getRightOperand();
                if( left instanceof LiteralElement literal && right instanceof Operand operand){
                    var operandReg = currentMethod.getVarTable().get(operand.getName());
                    if( reg.getVirtualReg() == operandReg.getVirtualReg() && !(assignOperand instanceof ArrayOperand)){
                        code.append("iinc ").append(reg.getVirtualReg()).append(" ").append(literal.getLiteral()).append(NL);
                        return code.toString();
                    }

                } else if (right instanceof LiteralElement literal && left instanceof Operand operand) {
                    var operandReg = currentMethod.getVarTable().get(operand.getName());
                    if( reg.getVirtualReg() == operandReg.getVirtualReg() && !(assignOperand instanceof ArrayOperand)){
                        code.append("iinc ").append(reg.getVirtualReg()).append(" ").append(literal.getLiteral()).append(NL);
                        return code.toString();
                    }
                }
            }
        }

        // generate code for loading what's on the right
        code.append(apply(rhs));

        if(rhs instanceof SingleOpInstruction){
            var op = (SingleOpInstruction) rhs;
            if(op.getSingleOperand() instanceof ArrayOperand){
                code.append("iaload").append(NL); //TODO: Assuming only int[]
                jasminUtils.setStackCounter(jasminUtils.getStackCounter() - 1);
            }
        }

        if(lhs instanceof ArrayOperand){
            code.append("iastore").append(NL); // TODO: Assuming only int[]
        }
        else {


            Type regType = reg.getVarType();
            String typePrefix;
            if (regType instanceof BuiltinType) {
                typePrefix = switch (((BuiltinType) regType).getKind()) {
                    case INT32, BOOLEAN -> "i";
                    case STRING -> "a";
                    case VOID -> "";
                };
            } else {
                typePrefix = "a";
            }
            String optString;
            if (reg.getVirtualReg() < 4) {
                optString = "_";
            } else {
                optString = " ";
            }

            code.append(typePrefix).append("store").append(optString).append(reg.getVirtualReg()).append(NL);
        }
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() - 1);
        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        var value = Integer.parseInt(literal.getLiteral());
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1);
        if(value == -1) return "iconst_m1" + NL;
        if( value >= 0 && value <= 5){
            return "iconst_" + value + NL;
        } else if( value >= -128 && value <= 127){
            return "bipush " + value + NL; 
        } else if (value >= -32768 && value <= 32767) {
            return "sipush " +  value + NL;
        }
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {

        StringBuilder code = new StringBuilder();
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        Type regType = reg.getVarType();
        String typePrefix = "";
        if( regType instanceof BuiltinType){
            typePrefix =  switch (((BuiltinType) regType).getKind()){
                case INT32, BOOLEAN -> "i";
                case STRING -> "a";
                case VOID -> "";
            };
        } else if ( regType instanceof ClassType) {
            switch (((ClassType) regType).getKind()){
                case THIS -> {
                    code.append("aload_0").append(NL);
                    jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1);
                    return code.toString();
                }
                case CLASS -> { // Static method ?
                    return "";
                }
                case OBJECTREF -> typePrefix = "a";
            }
        } else if ( regType instanceof ArrayType){
            typePrefix = "a";
        }
        String optString;
        if(reg.getVirtualReg() < 4){
            optString = "_";
        }
        else{
            optString = " ";
        }
        code.append(typePrefix).append("load").append(optString).append(reg.getVirtualReg()).append(NL);

        jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1);

        if(operand instanceof ArrayOperand){
            code.append(apply(((ArrayOperand) operand).getIndexOperands().get(0)));
        }

        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right

        Element leftOperand = binaryOp.getLeftOperand();
        Element rightOperand = binaryOp.getRightOperand();
        var isLeftZero = JasminUtils.isLiteralZero(leftOperand);
        var isRightZero = JasminUtils.isLiteralZero(rightOperand);


        if(isLeftZero){ // for comparisons to zero, it doesn't need to be on the stack
            code.append(apply(rightOperand));
        } else if (isRightZero) {
            code.append(apply(leftOperand));
        } else {
            code.append(apply(leftOperand));
            code.append(apply(rightOperand));
        }


        switch (binaryOp.getOperation().getOpType()){
            case LTH,GTH:
                String branchInstruction;
                switch (binaryOp.getOperation().getOpType()){
                    case LTH:
                        if(isRightZero){
                            branchInstruction = "iflt";
                        }
                        else if (isLeftZero){
                            branchInstruction = "ifgt";
                        }
                        else{
                            branchInstruction = "if_icmplt";
                        }
                        break;
                    case GTH:
                        if(isRightZero){
                            branchInstruction = "ifgt";
                        } else if (isLeftZero) {
                            branchInstruction = "iflt";
                        }
                        else {
                            branchInstruction = "if_icmpgt";
                        }
                        break;
                    default:
                        branchInstruction ="";
                        break;
                }
                String labelIdx;
                labelIdx = jasminUtils.nextCompareIdx();
                code.append(branchInstruction).append(" j_true_").append(labelIdx).append(NL);
                code.append("iconst_0").append(NL);
                code.append("goto  j_end").append(labelIdx).append(NL);
                code.append("j_true_").append(labelIdx).append(":").append(NL);
                code.append("iconst_1").append(NL);
                code.append("j_end").append(labelIdx).append(":").append(NL);
                break;
            case ADD,SUB,MUL,DIV:
                String op;
                op = switch (binaryOp.getOperation().getOpType()){
                    case ADD -> "iadd";
                    case SUB -> "isub";
                    case MUL -> "imul";
                    case DIV -> "idiv";
                    default -> "";

                };
                code.append(op).append(NL);
                break;
        }
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() -  1);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();


        Type returnType = currentMethod.getReturnType();
        String typePrefix;
        if( returnType instanceof BuiltinType){
            typePrefix= switch (((BuiltinType) returnType).getKind()){
                case INT32, BOOLEAN -> "i";
                case STRING -> "a";
                case VOID -> "";
            };
        } else {
            typePrefix = "a";
        }


        if(!(typePrefix.isEmpty())){
            code.append(apply(returnInst.getOperand().orElse(null)));
        }

        code.append(typePrefix).append("return");

        return code.toString();
    }

    private String generateCallInstruction(CallInstruction callInstruction) {
        return switch (callInstruction) {
            case ArrayLengthInstruction arrayLength -> generateArrayLengthInstruction(arrayLength);
            case InvokeSpecialInstruction invokeSpecial -> generateInvokeSpecial(invokeSpecial);
            case InvokeStaticInstruction invokeStatic -> generateInvokeStatic(invokeStatic);
            case InvokeVirtualInstruction invokeVirtual -> generateInvokeVirtual(invokeVirtual);
            case NewInstruction newInst -> generateNew(newInst);
            default -> throw new NotImplementedException(callInstruction);
        };
    }

    private String generateNew(NewInstruction newInst) {
        var code = new StringBuilder();

        if(newInst.getReturnType() instanceof ArrayType){

            code.append(apply(newInst.getArguments().getFirst()));
            code.append("newarray int").append(NL);
        } else if (newInst.getReturnType() instanceof ClassType) {
            var operandlElem = (Operand) newInst.getCaller();
            String classPath = jasminUtils.getClassPath(operandlElem.getName());
            code.append("new " + classPath);
            code.append(NL);
        }
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1);

        return code.toString();
    }

    private String generateArrayLengthInstruction(ArrayLengthInstruction arrayLengthInstruction) {
        var code = new StringBuilder();
        code.append(apply(arrayLengthInstruction.getCaller())).append(NL);
        code.append("arraylength").append(NL);
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1 );
        return code.toString();
    }

    private String generateFieldInstruction(FieldInstruction fieldInstruction){
        StringBuilder code = new StringBuilder();
        code.append("aload_0").append(NL);
        if(fieldInstruction instanceof PutFieldInstruction){
            code.append(apply(((PutFieldInstruction) fieldInstruction).getValue()));
            var fieldName = fieldInstruction.getField().getName();
            var fieldType = JasminUtils.convertType(fieldInstruction.getField().getType());

            code.append("putfield ").append(className).append("/").append(fieldName).append(" ").append(fieldType).append(NL);
            jasminUtils.setStackCounter(jasminUtils.getStackCounter() - 1);

        } else if (fieldInstruction instanceof GetFieldInstruction) {
            var fieldName = fieldInstruction.getField().getName();
            var fieldType = JasminUtils.convertType(fieldInstruction.getFieldType());

            code.append("getfield ").append(className).append("/").append(fieldName).append(" ").append(fieldType).append(NL);
            jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1 );
        }
        return code.toString();
    }

    private String generateCondBranchInstruction( CondBranchInstruction condBranchInstruction){

        StringBuilder code = new StringBuilder();
        code.append(apply(condBranchInstruction.getCondition()));
        code.append("ifne ").append(condBranchInstruction.getLabel()).append(NL);
        return code.toString();
    }

    private String generateGotoInstruction( GotoInstruction gotoInstruction){
        StringBuilder code = new StringBuilder();
        code.append("goto ").append(gotoInstruction.getLabel()).append(NL);
        return code.toString();
    }

    private String generateUnaryOpInstruction( UnaryOpInstruction unaryOpInstruction){
        StringBuilder code = new StringBuilder();
        code.append(apply(unaryOpInstruction.getOperand()));
        code.append("iconst_1").append(NL);
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() + 1);
        code.append("ixor").append(NL); // not is the only unary op for now
        jasminUtils.setStackCounter(jasminUtils.getStackCounter() - 1);

        return code.toString();

    }

    private String generateInvokeSpecial(InvokeSpecialInstruction invoke ){
        StringBuilder code = new StringBuilder();
        code.append(apply(invoke.getCaller()));
        var operandlElemType = invoke.getCaller().getType().toString();
        int start = operandlElemType.indexOf('(');
        int end = operandlElemType.indexOf(')');

        String name = operandlElemType.substring(start + 1, end);
        String classPath = jasminUtils.getClassPath(name);

        String typeString = JasminUtils.convertType(invoke.getReturnType());
        code.append("invokenonvirtual ").append(classPath).append("/<init>()").append(typeString).append(NL);

        jasminUtils.setStackCounter(jasminUtils.getStackCounter() - 1);

        return code.toString();
    }

    private String generateInvokeStatic(InvokeStaticInstruction invoke ){
        StringBuilder code = new StringBuilder();
        var operandElem = (Operand) invoke.getCaller();
        String classPath = jasminUtils.getClassPath(operandElem.getName());
        var literal = (LiteralElement) invoke.getMethodName();
        String argsCode = "";
        for(var args : invoke.getArguments()){
            code.append(apply(args));
            argsCode += JasminUtils.convertType(args.getType());
        }
        String returnType = JasminUtils.convertType(invoke.getReturnType());
        code.append("invokestatic ").append(classPath).append("/").append(literal.getLiteral()).append("(").append(argsCode).append(")")
                .append(returnType).append(NL);

        jasminUtils.setStackCounter(jasminUtils.getMaxStackCounter() - invoke.getArguments().size() + (returnType.equals("V") ? 0 : 1));

        if(!returnType.equals("V") && invoke.isIsolated()){
            code.append("pop").append(NL);
        }

        return code.toString();
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invoke ){
        StringBuilder code = new StringBuilder();
        code.append(apply(invoke.getCaller()));
        var operandlElemType = invoke.getCaller().getType().toString();
        int start = operandlElemType.indexOf('(');
        int end = operandlElemType.indexOf(')');
        String name = operandlElemType.substring(start + 1, end);
        String classPath = jasminUtils.getClassPath(name);

        var literal = (LiteralElement) invoke.getMethodName();
        String argsCode = "";
        for(var args : invoke.getArguments()){
            code.append(apply(args));
            argsCode += JasminUtils.convertType(args.getType());
        }

        String returnType = JasminUtils.convertType(invoke.getReturnType());

        code.append("invokevirtual ").append(classPath).append("/").append(literal.getLiteral()).append("(").append(argsCode).append(")")
                .append(returnType).append(NL);

        jasminUtils.setStackCounter(jasminUtils.getMaxStackCounter() - invoke.getArguments().size() + (returnType.equals("V") ? 0 : 1));


        if(!returnType.equals("V") && invoke.isIsolated()){
            code.append("pop").append(NL);
        }

        return code.toString();
    }

}