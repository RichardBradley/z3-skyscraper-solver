package org.bradders.z3sudoku;

import com.google.common.collect.Lists;
import com.microsoft.z3.*;
import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;

/**
 * Solves "skyscraper" sudoku like puzzles.
 *
 * Rules at https://www.gmpuzzles.com/blog/skyscrapers-rules-and-info/
 */
public class SkyscraperSolver {

    /**
     * The example given at https://www.gmpuzzles.com/blog/skyscrapers-rules-and-info/
     */
    static String[] example1 = new String[]{
            "   3 33 ",
            " ...... ",
            "2......4",
            " ......4",
            "2......4",
            "2...... ",
            " ...... ",
            "  5 5   ",
    };

    /**
     * From https://discourse.softwire.com/t/puzzles-that-i-gone-and-made/5786/4
     */
    static String[] samCL_2019_07_25 = new String[]{
            "   4    ",
            "3...... ",
            " ......5",
            "3...... ",
            " ......2",
            " ...... ",
            "4......2",
            "  2  2  ",
    };


    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        com.microsoft.z3.Global.ToggleWarningMessages(true);
        Log.open("test.log");
        HashMap<String, String> cfg = new HashMap<>();
        cfg.put("model", "true");
        Context ctx = new Context(cfg);

        solve(ctx, example1);
        solve(ctx, samCL_2019_07_25);

        System.out.println("Finished in " + (System.currentTimeMillis() - start) + "ms");
    }

    static void solve(Context ctx, String[] spec) throws Exception {
        System.out.println("Solving:");
        for (String s : spec) {
            System.out.println(" " + s);
        }
        Skyscraper puzz = parse(spec);
        solve(ctx, puzz);
    }

    static Skyscraper parse(String[] spec) {
        int width = spec[0].length() - 2;
        int height = spec.length - 2;
        assertThat(width).isEqualTo(height);
        for (String s : spec) {
            assertThat(s.length()).isEqualTo(width + 2);
        }
        assertThat(spec[0].charAt(0)).isEqualTo(' ');
        assertThat(spec[0].charAt(width + 1)).isEqualTo(' ');
        assertThat(spec[height + 1].charAt(0)).isEqualTo(' ');
        assertThat(spec[height + 1].charAt(width + 1)).isEqualTo(' ');
        Integer[] colLookingSouthSpecs = new Integer[width];
        Integer[] colLookingNorthSpecs = new Integer[width];
        Integer[] rowLookingEastSpecs = new Integer[height];
        Integer[] rowLookingWestSpecs = new Integer[height];
        for (int x = 0; x < width; x++) {
            colLookingSouthSpecs[x] = parseIntOrNull(spec[0].charAt(x + 1));
            colLookingNorthSpecs[x] = parseIntOrNull(spec[height + 1].charAt(x + 1));
        }
        for (int y = 0; y < height; y++) {
            rowLookingEastSpecs[y] = parseIntOrNull(spec[y + 1].charAt(0));
            rowLookingWestSpecs[y] = parseIntOrNull(spec[y + 1].charAt(width + 1));
        }
        Integer[][] initialValues = new Integer[width][height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                initialValues[x][y] = parseIntOrNull(spec[y + 1].charAt(x + 1));
            }
        }
        return new Skyscraper(
                width,
                initialValues,
                colLookingSouthSpecs,
                colLookingNorthSpecs,
                rowLookingEastSpecs,
                rowLookingWestSpecs);
    }

    private static Integer parseIntOrNull(char c) {
        return (c == ' ' || c == '.') ? null : (c - '0');
    }

    @Value
    private static class Skyscraper {
        int N; // N is both width and height, and cell values are 1..N
        Integer[][] initialValues;
        Integer[] colLookingSouthSpecs;
        Integer[] colLookingNorthSpecs;
        Integer[] rowLookingEastSpecs;
        Integer[] rowLookingWestSpecs;
    }

    static void solve(Context ctx, Skyscraper puzzle) throws Exception {
        int N = puzzle.N;

        // NxN matrix of integer variables
        IntExpr[][] X = new IntExpr[N][N];
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++)
                X[x][y] = (IntExpr) ctx.mkConst(
                        ctx.mkSymbol("x_" + (x + 1) + "_" + (y + 1)),
                        ctx.getIntSort());
        }

        // each cell contains a value in {1, ..., N}
        BoolExpr[][] cells_c = new BoolExpr[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++)
                cells_c[i][j] = ctx.mkAnd(ctx.mkLe(ctx.mkInt(1), X[i][j]),
                        ctx.mkLe(X[i][j], ctx.mkInt(N)));
        }

        // each row contains a digit at most once
        BoolExpr[] rows_c = new BoolExpr[N];
        for (int i = 0; i < N; i++)
            rows_c[i] = ctx.mkDistinct(X[i]);

        // each column contains a digit at most once
        BoolExpr[] cols_c = new BoolExpr[N];
        for (int j = 0; j < N; j++) {
            IntExpr[] col = new IntExpr[N];
            for (int i = 0; i < N; i++) {
                col[i] = X[i][j];
            }
            cols_c[j] = ctx.mkDistinct(col);
        }

        // add visibility rules
        BoolExpr vis_rules = ctx.mkTrue();
        for (int i = 0; i < N; i++) {
            if (null != puzzle.rowLookingEastSpecs[i]) {
                vis_rules = ctx.mkAnd(vis_rules,
                        mkVisbilityCondition(ctx, puzzle.rowLookingEastSpecs[i], Arrays.asList(X[i])));
            }
            if (null != puzzle.rowLookingWestSpecs[i]) {
                vis_rules = ctx.mkAnd(vis_rules,
                        mkVisbilityCondition(ctx, puzzle.rowLookingWestSpecs[i],
                                Lists.reverse(Arrays.asList(X[i]))));
            }
        }
        for (int j = 0; j < N; j++) {
            IntExpr[] col = new IntExpr[N];
            for (int i = 0; i < N; i++) {
                col[i] = X[i][j];
            }
            if (null != puzzle.colLookingSouthSpecs[j]) {
                vis_rules = ctx.mkAnd(vis_rules,
                        mkVisbilityCondition(ctx, puzzle.colLookingSouthSpecs[j], Arrays.asList(col)));
            }
            if (null != puzzle.colLookingNorthSpecs[j]) {
                vis_rules = ctx.mkAnd(vis_rules,
                        mkVisbilityCondition(ctx, puzzle.colLookingNorthSpecs[j],
                                Lists.reverse(Arrays.asList(col))));
            }
        }

        // Create general rules condition
        BoolExpr skyscraper_c = ctx.mkTrue();
        for (BoolExpr[] t : cells_c)
            skyscraper_c = ctx.mkAnd(ctx.mkAnd(t), skyscraper_c);
        skyscraper_c = ctx.mkAnd(ctx.mkAnd(rows_c), skyscraper_c);
        skyscraper_c = ctx.mkAnd(ctx.mkAnd(cols_c), skyscraper_c);

        // Create starting conditions
        BoolExpr instance_c = ctx.mkTrue();
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (null != puzzle.initialValues[i][j]) {
                    instance_c = ctx.mkAnd(
                            instance_c,
                            ctx.mkEq(X[i][j], ctx.mkInt(puzzle.initialValues[i][j])));
                }
            }
        }

        Solver s = ctx.mkSolver();
        s.add(skyscraper_c);
        s.add(vis_rules);
        s.add(instance_c);

        if (s.check() == Status.SATISFIABLE) {
            Model m = s.getModel();
            Expr[][] R = new Expr[N][N];
            for (int i = 0; i < N; i++)
                for (int j = 0; j < N; j++)
                    R[i][j] = m.evaluate(X[i][j], false);
            System.out.println("Solution:");
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++)
                    System.out.print(" " + R[i][j]);
                System.out.println();
            }
        } else {
            throw new Exception("Failed to solve");
        }
    }

    static BoolExpr mkVisbilityCondition(Context ctx, int visibleCount, List<IntExpr> cells) {
        if (cells.size() == 1) {
            checkArgument(visibleCount == 1);
            return ctx.mkTrue();
        }
        IntExpr first = cells.get(0);
        if (visibleCount == 1) {
            // only one building visible: first must be highest
            return ctx.mkAnd(cells.stream().skip(1).map(other -> ctx.mkGt(first, other)).toArray(BoolExpr[]::new));
        } else if (visibleCount > 1) {
            // 2 or more runs of decreasing building heights
            // Each run takes at least 1 building, so the max length of the first run is:
            int firstRunMaxLen = 1 + cells.size() - visibleCount;
            checkArgument(firstRunMaxLen >= 1);
            List<BoolExpr> options = new ArrayList<>();
            for (int firstRunLen = 1; firstRunLen <= firstRunMaxLen; firstRunLen++) {
                List<BoolExpr> firstRunCond = new ArrayList<>();
                for (int i = 1; i < firstRunLen; i++) {
                    firstRunCond.add(ctx.mkGt(first, cells.get(i)));
                }
                firstRunCond.add(ctx.mkLt(first, cells.get(firstRunLen)));
                options.add(ctx.mkAnd(array(
                        firstRunCond,
                        mkVisbilityCondition(
                                ctx,
                                visibleCount - 1,
                                cells.subList(firstRunLen, cells.size())))));
            }
            return options.size() == 1 ? options.get(0) : ctx.mkOr(options.toArray(new BoolExpr[0]));
        } else {
            throw new IllegalArgumentException("visibleCount: " + visibleCount);
        }
    }

    private static BoolExpr[] array(List<BoolExpr> a, BoolExpr b) {
        BoolExpr[] acc = new BoolExpr[a.size() + 1];
        a.toArray(acc);
        acc[a.size()] = b;
        return acc;
    }
}
