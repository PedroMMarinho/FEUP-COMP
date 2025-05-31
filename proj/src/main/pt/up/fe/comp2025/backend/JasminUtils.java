package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    private int jumpTrueLabelIdx = 0;

    private int stackCounter = 0;

    private int maxStackCounter = 0;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }


    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public static String convertType(Type type){
        if( type instanceof BuiltinType ){
            return switch ( ((BuiltinType) type).getKind()){
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case STRING -> "Ljava/lang/String;";
                case VOID -> "V";
            };
        }
        if( type instanceof ArrayType){

            return "[" + convertType(((ArrayType) type).getElementType()); // arrays can only be int[] according to specification
        }
        if( type instanceof ClassType){
            return "L" + ((ClassType) type).getName() + ";";
        }
        return null;
    }

    public String nextCompareIdx(){
        return String.valueOf(jumpTrueLabelIdx++);
    }

    public static boolean isLiteralZero(Element operand){
        return operand instanceof LiteralElement && ((LiteralElement) operand).getLiteral().equals("0");
    }

    public String getClassPath(String name){
        if (ollirResult.getOllirClass().getClassName().equals(name)){
            return name;
        }
        else{
            for(var importPath : ollirResult.getOllirClass().getImports()){
                String[] parts = importPath.split("\\.");
                String importClassName = parts[parts.length - 1];
                if(importClassName.equals(name)){
                    return String.join("/", parts);
                }
            }
        }
        return "";
    }

    public void setStackCounter(int stackCounter) {
        this.stackCounter = stackCounter;
        maxStackCounter = Math.max(stackCounter,maxStackCounter);
    }

    public int getStackCounter(){
        return stackCounter;
    }

    public int getMaxStackCounter(){
        return maxStackCounter;
    }


}
