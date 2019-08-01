package org.bradders.z3sudoku;

import com.google.common.collect.ImmutableList;
import com.microsoft.z3.Context;
import com.microsoft.z3.IntExpr;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class SkyscraperSolverTest {

    @Test
    public void testMkVisbilityCondition() {
        Context ctx = new Context();

        ImmutableList<IntExpr> cells = ImmutableList.of(
                ctx.mkIntConst("a"),
                ctx.mkIntConst("b"),
                ctx.mkIntConst("c"),
                ctx.mkIntConst("d"));

        assertThat(SkyscraperSolver.mkVisbilityCondition(
                ctx,
                1,
                cells).toString())
                .isEqualTo("(and (> a b) (> a c) (> a d))");

        assertThat(SkyscraperSolver.mkVisbilityCondition(
                ctx,
                2,
                cells).toString())
                .isEqualTo(
                        "(or (and (< a b) (> b c) (> b d))\n" +
                                "    (and (> a b) (< a c) (> c d))\n" +
                                "    (and (> a b) (> a c) (< a d) true))");
    }
}
