/**
 * Copyright 2022 SPeCS.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

package pt.up.fe.comp.cp2;

import org.junit.Test;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.ConfigOptions;
import pt.up.fe.specs.util.SpecsIo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OptimizationsTest {
    private static final String BASE_PATH = "pt/up/fe/comp/cp2/optimizations/";

    static OllirResult getOllirResult(String filename) {
        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), Collections.emptyMap(), false);
    }

    static OllirResult getOllirResultOpt(String filename) {
        Map<String, String> config = new HashMap<>();
        config.put(ConfigOptions.getOptimize(), "true");

        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), config, true);
    }

    static OllirResult getOllirResultRegalloc(String filename, int maxRegs) {
        Map<String, String> config = new HashMap<>();
        config.put(ConfigOptions.getRegister(), Integer.toString(maxRegs));


        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), config, true);
    }

    @Test
    public void regAllocSimple() {

        String filename = "reg_alloc/regalloc_no_change.jmm";
        int expectedTotalReg = 4;
        int configMaxRegs = 2;

        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "soManyRegisters"));

        // Number of registers might change depending on what temporaries are generated, no use comparing with original

        CpUtils.assertTrue("Expected number of locals in 'soManyRegisters' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);


        var varTable = CpUtils.getMethod(optimized, "soManyRegisters").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertNotEquals("Expected registers of variables 'a' and 'b' to be different", aReg, varTable.get("b").getVirtualReg(), optimized);
    }

    @Test
    public void regAllocWithParametersTest() {
        String filename = "reg_alloc/method_with_params.jmm";


        int expectedTotalReg = 5;
        int configMaxRegs = 3;

        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        Method method = CpUtils.getMethod(optimized, "methodWithParams");

        int actualNumReg = CpUtils.countRegisters(method);

        CpUtils.assertTrue(
                "Expected number of registers in 'methodWithParams' to be " + expectedTotalReg + ", but got " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized
        );

        var varTable = method.getVarTable();

        int thisReg = varTable.get("this").getVirtualReg();
        CpUtils.assertEquals("Expected 'this' to be in register 0", 0, thisReg, optimized);

        int paramAReg = varTable.get("a").getVirtualReg();
        int paramBReg = varTable.get("b").getVirtualReg();
        CpUtils.assertTrue(
                "Expected parameter 'a' to be in register 1",
                paramAReg == 1,
                optimized
        );
        CpUtils.assertTrue(
                "Expected parameter 'b' to be in register 2",
                paramBReg == 2,
                optimized
        );

        int localCReg = varTable.get("c").getVirtualReg();
        int localDReg = varTable.get("d").getVirtualReg();
        int localEReg = varTable.get("e").getVirtualReg();

        CpUtils.assertTrue(
                "Expected local variable 'c' to use register ≥ 3",
                localCReg >= 3,
                optimized
        );

        CpUtils.assertTrue(
                "Expected local variable 'd' to use register ≥ 3",
                localDReg >= 3,
                optimized
        );

        CpUtils.assertTrue(
                "Expected local variable 'e' to use register ≥ 3",
                localEReg >= 3,
                optimized
        );

        CpUtils.assertEquals(
                "Expected 'd' and 'e' to share the same register",
                localDReg,
                localEReg,
                optimized
        );

        CpUtils.assertNotEquals(
                "Expected 'c' to use a different register than 'd' and 'e'",
                localCReg,
                localDReg,
                optimized
        );
    }




    @Test
    public void regAllocSequence() {

        String filename = "reg_alloc/regalloc.jmm";
        int expectedTotalReg = 3;
        int configMaxRegs = 1;

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultRegalloc(filename, configMaxRegs);

        int originalNumReg = CpUtils.countRegisters(CpUtils.getMethod(original, "soManyRegisters"));
        int actualNumReg = CpUtils.countRegisters(CpUtils.getMethod(optimized, "soManyRegisters"));

        CpUtils.assertNotEquals("Expected number of registers to change with -r flag\n\nOriginal regs:" + originalNumReg + "\nNew regs: " + actualNumReg,
                originalNumReg, actualNumReg,
                optimized);

        CpUtils.assertTrue("Expected number of locals in 'soManyRegisters' to be equal to " + expectedTotalReg + ", is " + actualNumReg,
                actualNumReg == expectedTotalReg,
                optimized);


        var varTable = CpUtils.getMethod(optimized, "soManyRegisters").getVarTable();
        var aReg = varTable.get("a").getVirtualReg();
        CpUtils.assertEquals("Expected registers of variables 'a' and 'b' to be the same", aReg, varTable.get("b").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'c' to be the same", aReg, varTable.get("c").getVirtualReg(), optimized);
        CpUtils.assertEquals("Expected registers of variables 'a' and 'd' to be the same", aReg, varTable.get("d").getVirtualReg(), optimized);

    }


    @Test
    public void constPropSimple() {

        String filename = "const_prop_fold/PropSimple.jmm";

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultOpt(filename);

        CpUtils.assertNotEquals("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode(), optimized.getOllirCode(),
                optimized);

        var method = CpUtils.getMethod(optimized, "foo");
        CpUtils.assertLiteralReturn("1", method, optimized);
    }

    @Test
    public void constPropWithLoop() {

        String filename = "const_prop_fold/PropWithLoop.jmm";

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultOpt(filename);

        CpUtils.assertNotEquals("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode(), optimized.getOllirCode(),
                optimized);

        var method = CpUtils.getMethod(optimized, "foo");
        CpUtils.assertLiteralCount("3", method, optimized, 3);
    }

    @Test
    public void constFoldSimple() {

        String filename = "const_prop_fold/FoldSimple.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("30", method, optimized);
    }

    @Test
    public void constFoldSequence() {

        String filename = "const_prop_fold/FoldSequence.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("14", method, optimized);
    }

    @Test
    public void constPropAnFoldSimple() {

        String filename = "const_prop_fold/PropAndFoldingSimple.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "main");
        CpUtils.assertFindLiteral("15", method, optimized);
    }

    @Test
    public void propIfSpam(){
        String filename =  "extra_tests/PropIfSpam.jmm";

        OllirResult original = getOllirResult(filename);
        OllirResult optimized = getOllirResultOpt(filename);

        CpUtils.assertNotEquals("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode(), optimized.getOllirCode(),
                optimized);

        var method = CpUtils.getMethod(optimized, "foo");
        CpUtils.assertLiteralCount("1", method, optimized, 5);
        CpUtils.assertLiteralCount("2",method,optimized,7);
        CpUtils.assertLiteralCount("3", method,optimized,1);


    }

    @Test
    public void nestedIf() {
        String filename = "extra_tests/NestedIf.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "test");
        System.out.println();
        CpUtils.assertLiteralCount("0", method, optimized, 2);
        CpUtils.assertLiteralCount("4", method, optimized, 2);
        CpUtils.assertLiteralCount("1", method, optimized, 2);
    }

    @Test
    public void whileWithIf() {
        String filename = "extra_tests/WhileWithIf.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code not to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "B");
        CpUtils.assertLiteralCount("0", method, optimized, 1);
        CpUtils.assertLiteralCount("1", method, optimized, 2);
        CpUtils.assertLiteralCount("3", method, optimized, 1);
    }

    @Test
    public void nestedWhile(){
        String filename = "extra_tests/NestedWhile.jmm";

        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);


        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "test");
        CpUtils.assertLiteralCount("0", method, optimized, 1);
        CpUtils.assertLiteralCount("2", method, optimized, 1);
        CpUtils.assertLiteralCount("4", method, optimized, 1);
        CpUtils.assertLiteralCount("5", method, optimized, 1);
        CpUtils.assertLiteralCount("6", method, optimized, 1);

    }

    @Test
    public void ifWithWhile(){
        String filename = "extra_tests/IfWithWhile.jmm";
        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);

        CpUtils.assertTrue("Expected code to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                !original.getOllirCode().equals(optimized.getOllirCode()), optimized);

        var method = CpUtils.getMethod(optimized, "B");
        CpUtils.assertLiteralCount("0", method, optimized, 1);
        CpUtils.assertLiteralCount("1", method, optimized, 3);
        CpUtils.assertLiteralCount("2", method, optimized, 2);
        CpUtils.assertLiteralCount("3", method, optimized, 1);

    }

    @Test
    public void nestedLoops(){
        String filename = "extra_tests/NestedLoops.jmm";
        var original = getOllirResult(filename);
        var optimized = getOllirResultOpt(filename);

        CpUtils.assertTrue("Expected code not to change with -o flag\n\nOriginal code:\n" + original.getOllirCode(),
                original.getOllirCode().equals(optimized.getOllirCode()), optimized);

    }

}
