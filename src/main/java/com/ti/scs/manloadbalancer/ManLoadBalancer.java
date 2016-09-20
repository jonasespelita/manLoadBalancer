package com.ti.scs.manloadbalancer;

import com.ti.scs.parampermutator.ParamPermutator;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static java.util.stream.Collectors.*;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

/**
 *
 * @author a0284021
 */
public class ManLoadBalancer {

    public static void main(String[] args) {

        final int M = 200;

        // Dq, Ph, Ec,  Ep
        final int[][] I = {
            {200000, 200, 899, 5},
            {10000, 20, 100, 10},
            {100, 1, 40, 1},
            {2000, 100, 20, 5}
        };

        // generate gen weight alloc
        final double[] maxAlloc = Stream.of(I)
                .mapToDouble(i -> (double) i[2] / i[3]).peek(System.out::println)
                .toArray();

        // max people alloc
        final double smp = DoubleStream.of(maxAlloc).peek(System.out::println).sum();
        System.out.println("smp = " + smp);

        // general weighted average allocation
        int[] gwaa = getGwa(maxAlloc, smp, M);

        // account for rounding error
        final int sum = IntStream.of(gwaa).sum();
        if (sum != M) {
            gwaa[0] += M - sum;
        }

        assert IntStream.of(gwaa).sum() == M;
        System.out.println("gwaa = " + intArToStr(gwaa));

        // seed param with gwaa
        double bestSD = Integer.MAX_VALUE;
        int[] param = gwaa;
//        int[] param = {97, 1, 1, 1};
        int iterations = 0;
        while (true) {
            // generate all permutations for PARAM
            final List<int[]> permutations = ParamPermutator.permutate(param);
            int[] bestPerm = permutations.stream()
                    .parallel()
                    .reduce(param, (x, y) -> {
                        // check param > 0
                        if (IntStream.of(y).min().getAsInt() == 0) {
                            return x;
                        }

                        double xSDds = getSdofDs(x, I);
                        double ySDds = getSdofDs(y, I);
                        // param with least SD survives
                        if (xSDds < ySDds) {
                            // param has more SD, keep previous
                            return x;
                        } else {
                            // new param has better SD, see if survives
                            // check that vals are still within max
                            for (int i = 0; i < maxAlloc.length; i++) {
                                if (y[i] > maxAlloc[i]) {
                                    System.out.println("Exceeded max alloc " + intArToStr(y) + " " + i);
                                    return x;
                                }
                            }
                            // param survives, set as new king
                            return y;
                        }
                    });

            List<Integer> bestPermStr = intArToStr(bestPerm);
            System.out.println("bestPermStr = " + bestPermStr);

            List<Double> bestPermDs = IntStream.range(0, bestPerm.length).mapToDouble(i -> {
                return getDs(I, i, bestPerm[i]);
            }).boxed().collect(toList());
            System.out.println("bestPermDs = " + bestPermDs);
            param = bestPerm;

            double curSD = getSdofDs(param, I);
            iterations++;
            if (curSD == bestSD) {
                break;
            } else {
                bestSD = curSD;
            }
        }

        System.out.println("Took " + iterations + " iterations");

    }

    private static int[] getGwa(final double[] maxAlloc, final double smp, final int M) {
        // get general weigted average
        final double[] gwa = DoubleStream.of(maxAlloc)
                .map(y -> y / smp * M)
                .toArray();
        // floor gwa into allocations
        final int[] gwaa = DoubleStream.of(gwa).mapToInt(y -> (int) Math.floor(y)).toArray();
        return gwaa;
    }

    private static List<Integer> intArToStr(final int[] gwaa) {
        return IntStream.of(gwaa).boxed().collect(toList());
    }

    private static double getSdofDs(int[] perm, final int[][] I) {
        SummaryStatistics stats = new SummaryStatistics();
        IntStream.range(0, perm.length).mapToDouble(i -> {
            // compute ds fore each perms param
            // Th is person alloc
            int Th = perm[i];
            return getDs(I, i, Th);
        }).forEach(i -> stats.addValue(i));
        return stats.getStandardDeviation();
    }

    private static double getDs(final int[][] I, int i, int Th) {

        int Dq = I[i][0];
        int Ph = I[i][1];
        int Ec = I[i][2];
        int Ep = I[i][3];
        double ds = Math.min(Dq, Math.min(Ec, Th * Ep) * Ph) / (double) Dq;
//        System.out.println("ds = " + ds);
        return ds;
    }

}
