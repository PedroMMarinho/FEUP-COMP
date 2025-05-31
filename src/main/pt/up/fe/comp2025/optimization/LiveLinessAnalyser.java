package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import org.specs.comp.ollir.VarScope;
import org.specs.comp.ollir.inst.*;

import java.util.*;

import static org.specs.comp.ollir.InstructionType.ASSIGN;

public class LiveLinessAnalyser {

    private HashMap<Integer, List<String>> in;
    private HashMap<Integer, List<String>> out;
    private HashMap<Integer, String> def;
    private HashMap<Integer, List<String>> use;

    LiveLinessAnalyser (){
        this.in = new HashMap<>();
        this.out = new HashMap<>();
        this.def = new HashMap<>();
        this.use = new HashMap<>();
    }

    public List<HashMap<Integer, List<String>>> computeAnalysis(Method method){
        this.in.clear();
        this.out.clear();
        this.def.clear();
        this.use.clear();

        var instructions = method.getInstructions();

        for (var instruction : instructions) {
            in.put(instruction.getId(), new ArrayList<>());
            out.put(instruction.getId(), new ArrayList<>());
            def.put(instruction.getId(), getVarsDefined(instruction, method));
            use.put(instruction.getId(), getVarsUsed(instruction, method));
        }

        boolean hasChanged;

        do{
            hasChanged = false;

            for (int i = instructions.size()-1; i >= 0; i--) {
                var instruction = instructions.get(i);

                // Initialize the sets Live_in' and Live_out'
                List<String> inVars = new ArrayList<>(in.get(instruction.getId()));
                List<String> outVars = new ArrayList<>(out.get(instruction.getId()));

                // Update the Live_out unifying all succ of Live in
                out.put(instruction.getId(), getInVarsSuccessors(instruction));

                // Update the Live_in unifying use with the difference of Live_out and def
                var live_out = new ArrayList<>(out.get(instruction.getId()));
                var def_difference = def.get(instruction.getId());

                if (def_difference != null)
                    live_out.remove(def_difference);

                Set<String> union = new HashSet<>();
                union.addAll(live_out);
                union.addAll(use.get(instruction.getId()));
                var union_out = new ArrayList<>(union);

                in.put(instruction.getId(),union_out);


                // Intersection between Live_in with Live_out'
                outVars.retainAll(in.get(instruction.getId()));

                if (in.get(instruction.getId()).stream().anyMatch(inVars::contains))
                {
                    hasChanged = true;
                    continue;
                }

                if (out.get(instruction.getId()).stream().anyMatch(outVars::contains)){
                    hasChanged = true;
                }
            }

        } while (hasChanged);

        return Arrays.asList(in, out);
    }

    private List<String> getInVarsSuccessors(Instruction instruction) {
        List<String> inVarsSuccessors = new ArrayList<>();

        for (var suc : instruction.getSuccessors()) {
            var inVarSuc = in.get(suc.getId());

            if (inVarSuc != null) {
                inVarsSuccessors.addAll(inVarSuc);
            }
        }

        return inVarsSuccessors;
    }

    private String getVarsDefined(Instruction instruction, Method method) {
        if(instruction.getInstType().equals(ASSIGN)){
            var assignInstruction = (AssignInstruction) instruction;
            String varName = ((Operand)assignInstruction.getDest()).getName();
            if (isLocalVariable(varName, method)) {
                return varName;
            }
        }
        return null;
    }

    private List<String> getVarsUsed(Instruction instruction, Method method) {
        return switch (instruction.getInstType()) {
            case ASSIGN -> getVarsUsedSpecific((AssignInstruction) instruction, method);
            case CALL -> getVarsUsedSpecific((CallInstruction) instruction, method);
            case GOTO -> getVarsUsedSpecific((GotoInstruction) instruction, method);
            case BRANCH -> getVarsUsedSpecific((CondBranchInstruction) instruction, method);
            case RETURN -> getVarsUsedSpecific((ReturnInstruction) instruction, method);
            case PUTFIELD -> getVarsUsedSpecific((PutFieldInstruction) instruction, method);
            case GETFIELD -> getVarsUsedSpecific((GetFieldInstruction) instruction, method);
            case UNARYOPER -> getVarsUsedSpecific((UnaryOpInstruction) instruction, method);
            case BINARYOPER -> getVarsUsedSpecific((BinaryOpInstruction) instruction, method);
            case NOPER -> getVarsUsedSpecific((SingleOpInstruction) instruction, method);
            case LDC -> throw new RuntimeException("LDC instruction is not supported by this compiler");
        };
    }

    private List<String> getVarsUsedSpecific(SingleOpInstruction instruction, Method method) {
        var opName = getOperandName(instruction.getSingleOperand());

        if (opName == null || !isLocalVariable(opName, method)) return new ArrayList<>();

        return Collections.singletonList(opName);
    }

    private List<String> getVarsUsedSpecific(CondBranchInstruction instruction, Method method){
        if (instruction instanceof SingleOpCondInstruction) {
            return getVarsUsedSpecific(((SingleOpCondInstruction) instruction).getCondition(), method);
        }
        return new ArrayList<>();
    }

    private List<String> getVarsUsedSpecific(BinaryOpInstruction instruction, Method method) {
        List<String> l = new ArrayList<>();

        var operand1 = getOperandName(instruction.getLeftOperand());
        var operand2 = getOperandName(instruction.getRightOperand());

        if (operand1 != null && isLocalVariable(operand1, method)) l.add(operand1);
        if (operand2 != null && isLocalVariable(operand2, method)) l.add(operand2);

        return l;
    }

    private List<String> getVarsUsedSpecific(GetFieldInstruction instruction, Method method) {
        var fieldName = instruction.getField().getName();
        if (isLocalVariable(fieldName, method)) {
            return Collections.singletonList(fieldName);
        }
        return new ArrayList<>();
    }

    private List<String> getVarsUsedSpecific(UnaryOpInstruction instruction, Method method) {
        var opName = getOperandName(instruction.getOperand());

        if (opName == null || !isLocalVariable(opName, method)) return new ArrayList<>();

        return Collections.singletonList(opName);
    }

    private List<String> getVarsUsedSpecific(PutFieldInstruction instruction, Method method) {
        List<String> l = new ArrayList<>();

        var opName1 = getOperandName(instruction.getField());
        var opName2 = getOperandName(instruction.getOperands().get(2));

        if (opName1 != null && isLocalVariable(opName1, method)) l.add(opName1);
        if (opName2 != null && isLocalVariable(opName2, method)) l.add(opName2);

        return l;
    }

    private List<String> getVarsUsedSpecific(ReturnInstruction instruction, Method method) {
        if (!instruction.hasReturnValue()) return new ArrayList<>();
        var operandName = getOperandName(instruction.getOperand().orElse(null));

        if (operandName == null || !isLocalVariable(operandName, method)) return new ArrayList<>();

        return Collections.singletonList(operandName);
    }

    private List<String> getVarsUsedSpecific(GotoInstruction instruction, Method method) {
        return new ArrayList<>();
    }

    private List<String> getVarsUsedSpecific(AssignInstruction instruction, Method method) {
        return getVarsUsed(instruction.getRhs(), method);
    }

    private List<String> getVarsUsedSpecific(CallInstruction instruction, Method method) {
        List<String> l = new ArrayList<>();

        for (var operand : instruction.getOperands()) {
            var opName = getOperandName(operand);
            if (opName != null && isLocalVariable(opName, method)) {
                l.add(opName);
            }
        }

        return l;
    }

    private String getOperandName(Element element) {
        if (element instanceof Operand) {
            return ((Operand) element).getName();
        }
        return null;
    }

    private boolean isLocalVariable(String name, Method method) {
        var varTable = method.getVarTable();
        return varTable.containsKey(name) &&
                varTable.get(name).getScope() == VarScope.LOCAL;
    }
}