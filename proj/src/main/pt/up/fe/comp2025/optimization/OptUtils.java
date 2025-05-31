package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;

import static pt.up.fe.comp2025.ast.Kind.TYPE;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {


    private final AccumulatorMap<String> temporaries;
    private final AccumulatorMap<String> thenLabels;
    private final AccumulatorMap<String> endLabels;
    private final AccumulatorMap<String> whileLabels;
    private final AccumulatorMap<String> andLabels;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
        this.thenLabels = new AccumulatorMap<>();
        this.endLabels = new AccumulatorMap<>();
        this.whileLabels = new AccumulatorMap<>();
        this.andLabels = new AccumulatorMap<>();
    }


    public String nextTemp() {

        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {

        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;

        return prefix + nextTempNum;
    }
    public void revertTemp(String prefix ){
        int currentCount = temporaries.getCount(prefix);
        if(currentCount > 0){
            temporaries.remove("tmp");
        }

    }

    public String nextThenLabel() {

        return nextThenLabel("then");
    }

    public String nextThenLabel(String prefix) {

        // Subtract 1 because the base is 1
        var nextLabel = thenLabels.add(prefix) - 1;

        return prefix + nextLabel;
    }

    public String nextEndLabel() {

        return nextEndLabel("endif");
    }

    public String nextEndLabel(String prefix) {

        // Subtract 1 because the base is 1
        var nextLabel = endLabels.add(prefix) - 1;

        return prefix + nextLabel;
    }

    public String nextWhileLabel() {

        return nextWhileLabel("while");
    }

    public String nextWhileLabel(String prefix) {

        // Subtract 1 because the base is 1
        var nextLabel = whileLabels.add(prefix) - 1;

        return prefix + nextLabel;
    }

    public String nextAndLabel() {

        return nextAndLabel("andTmp");
    }

    public String nextAndLabel(String prefix) {

        // Subtract 1 because the base is 1
        var nextLabel = andLabels.add(prefix) - 1;

        return prefix + nextLabel;
    }

    public String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        return toOllirType(types.convertType(typeNode));
    }

    public String toOllirType(Type type) {
        String ollirType = toOllirType(type.getName());
        String code = ollirType;
        if (type.isArray() || TypeUtils.isVararg(type)){
            code = ".array" + ollirType ;
        }
        return code;
    }

    private String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "String" -> "String";
            default -> typeName;
            //default -> throw new NotImplementedException(typeName);
        };

        return type;
    }


}
