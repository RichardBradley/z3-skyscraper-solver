package org.bradders.z3sudoku;

import com.microsoft.z3.*;

import java.util.HashMap;

public class SudokuSolver {

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        com.microsoft.z3.Global.ToggleWarningMessages(true);
        Log.open("test.log");
        HashMap<String, String> cfg = new HashMap<String, String>();
        cfg.put("model", "true");
        Context ctx = new Context(cfg);
        sudokuExample(ctx);
        System.out.println("Finished in " + (System.currentTimeMillis() - start) + "ms");
    }


    /**
     * https://github.com/Z3Prover/z3/blob/master/examples/java/JavaExample.java#L489
     */
    static void sudokuExample(Context ctx) throws Exception {
        System.out.println("SudokuExample");
        Log.append("SudokuExample");

        // 9x9 matrix of integer variables
        IntExpr[][] X = new IntExpr[9][];
        for (int i = 0; i < 9; i++) {
            X[i] = new IntExpr[9];
            for (int j = 0; j < 9; j++)
                X[i][j] = (IntExpr) ctx.mkConst(
                        ctx.mkSymbol("x_" + (i + 1) + "_" + (j + 1)),
                        ctx.getIntSort());
        }

        // each cell contains a value in {1, ..., 9}
        BoolExpr[][] cells_c = new BoolExpr[9][];
        for (int i = 0; i < 9; i++) {
            cells_c[i] = new BoolExpr[9];
            for (int j = 0; j < 9; j++)
                cells_c[i][j] = ctx.mkAnd(ctx.mkLe(ctx.mkInt(1), X[i][j]),
                        ctx.mkLe(X[i][j], ctx.mkInt(9)));
        }

        // each row contains a digit at most once
        BoolExpr[] rows_c = new BoolExpr[9];
        for (int i = 0; i < 9; i++)
            rows_c[i] = ctx.mkDistinct(X[i]);

        // each column contains a digit at most once
        BoolExpr[] cols_c = new BoolExpr[9];
        for (int j = 0; j < 9; j++) {
            IntExpr[] col = new IntExpr[9];
            for (int i = 0; i < 9; i++) {
                col[i] = X[i][j];
            }
            cols_c[j] = ctx.mkDistinct(col);
        }

        // each 3x3 square contains a digit at most once
        BoolExpr[][] sq_c = new BoolExpr[3][];
        for (int i0 = 0; i0 < 3; i0++) {
            sq_c[i0] = new BoolExpr[3];
            for (int j0 = 0; j0 < 3; j0++) {
                IntExpr[] square = new IntExpr[9];
                for (int i = 0; i < 3; i++)
                    for (int j = 0; j < 3; j++)
                        square[3 * i + j] = X[3 * i0 + i][3 * j0 + j];
                sq_c[i0][j0] = ctx.mkDistinct(square);
            }
        }

        BoolExpr sudoku_c = ctx.mkTrue();
        for (BoolExpr[] t : cells_c)
            sudoku_c = ctx.mkAnd(ctx.mkAnd(t), sudoku_c);
        sudoku_c = ctx.mkAnd(ctx.mkAnd(rows_c), sudoku_c);
        sudoku_c = ctx.mkAnd(ctx.mkAnd(cols_c), sudoku_c);
        for (BoolExpr[] t : sq_c)
            sudoku_c = ctx.mkAnd(ctx.mkAnd(t), sudoku_c);

        // sudoku instance, we use '0' for empty cells
        int[][] instance = {
                {0, 0, 0, 0, 9, 4, 0, 3, 0},
                {0, 0, 0, 5, 1, 0, 0, 0, 7},
                {0, 8, 9, 0, 0, 0, 0, 4, 0},
                {0, 0, 0, 0, 0, 0, 2, 0, 8},
                {0, 6, 0, 2, 0, 1, 0, 5, 0},
                {1, 0, 2, 0, 0, 0, 0, 0, 0},
                {0, 7, 0, 0, 0, 0, 5, 2, 0},
                {9, 0, 0, 0, 6, 5, 0, 0, 0},
                {0, 4, 0, 9, 7, 0, 0, 0, 0}};

        BoolExpr instance_c = ctx.mkTrue();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (0 != instance[i][j]) {
                    instance_c = ctx.mkAnd(
                            instance_c,
                            ctx.mkEq(X[i][j], ctx.mkInt(instance[i][j])));
                }
            }
        }

        Solver s = ctx.mkSolver();
        s.add(sudoku_c);
        s.add(instance_c);

        if (s.check() == Status.SATISFIABLE) {
            Model m = s.getModel();
            Expr[][] R = new Expr[9][9];
            for (int i = 0; i < 9; i++)
                for (int j = 0; j < 9; j++)
                    R[i][j] = m.evaluate(X[i][j], false);
            System.out.println("Sudoku solution:");
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++)
                    System.out.print(" " + R[i][j]);
                System.out.println();
            }
        } else {
            System.out.println("Failed to solve sudoku");
            throw new Exception();
        }
    }

}
