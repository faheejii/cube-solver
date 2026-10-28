package solver;

import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manually invoked diagnostic benchmark for the current F2L solver.
 *
 * <p>This class deliberately does not use a JUnit test suffix and has no
 * {@code @Test} methods, so normal Surefire runs do not discover it. It runs
 * production cross, F2L, OLL, and PLL solvers; reported wall-clock times are
 * diagnostic only and are not test assertions.</p>
 *
 * <p>Run from the repository root after compiling test sources:</p>
 * <pre>
 * mvn -q -DskipTests test-compile dependency:build-classpath \
 *   -Dmdep.outputFile=/tmp/cube-solver-test-classpath.txt
 * java -cp "target/test-classes:target/classes:$(cat /tmp/cube-solver-test-classpath.txt)" \
 *   solver.F2LOptimizationBenchmark
 * </pre>
 */
public final class F2LOptimizationBenchmark {
    private static final long SEED = 20261027L;
    private static final int SCRAMBLE_COUNT = 100;
    private static final int SCRAMBLE_LENGTH = 25;
    private static final Face[] CROSS_FACES = {Face.U, Face.D, Face.F, Face.B, Face.L, Face.R};
    private static final Face[] SCRAMBLE_FACES = {Face.U, Face.D, Face.F, Face.B, Face.L, Face.R};
    private static final String[] TURN_SUFFIXES = {"", "2", "'"};
    private static final long OPTIMIZED_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(
            Long.getLong("f2l.benchmark.budget-seconds", 0L)
    );

    private F2LOptimizationBenchmark() {
    }

    public static void main(String[] args) {
        var cases = prepareCases();
        var initializationStart = System.nanoTime();
        var f2lSolver = new F2LSolver();
        var ollSolver = new OLLSolver(OLLCaseDatabase.seedCases());
        var pllSolver = new PLLSolver(PLLCaseDatabase.seedCases());
        long initializationNanos = System.nanoTime() - initializationStart;

        // Warm the production paths on fixed cases outside the measured sample.
        for (int i = 0; i < Math.min(5, cases.size()); i++) {
            var cube = cases.get(i).postCross();
            var fast = f2lSolver.solveTrace(copy(cube));
            f2lSolver.solveOptimizedCandidates(copy(cube), ignored -> { }, fast.algorithm());
        }

        var fastSamples = new ArrayList<Sample>();
        var optimizedSamples = new ArrayList<Sample>();
        var continuationSamples = new ArrayList<Sample>();

        for (int index = 0; index < cases.size(); index++) {
            var benchmarkCase = cases.get(index);
            var fastStart = System.nanoTime();
            var fastTrace = f2lSolver.solveTrace(copy(benchmarkCase.postCross()));
            long fastNanos = System.nanoTime() - fastStart;
            fastSamples.add(new Sample(fastNanos, fastTrace.algorithm().getMoveCount(),
                    fastTrace.solved() ? 1 : 0, 1, 0, 0, 0, 0, 0));

            var latestProgress = new ProgressAccumulator();
            var fastContinuation = evaluateContinuation(
                    benchmarkCase.postCross(), benchmarkCase.crossMoveCount(),
                    fastTrace.algorithm(), ollSolver, pllSolver
            );
            var bestContinuation = new AtomicReference<>(fastContinuation != null && fastContinuation.fullySolved()
                    ? fastContinuation : null);
            var evaluatedCandidates = new AtomicInteger();
            var optimizedStart = System.nanoTime();
            long deadlineNanos = OPTIMIZED_BUDGET_NANOS == 0
                    ? Long.MAX_VALUE : System.nanoTime() + OPTIMIZED_BUDGET_NANOS;
            var optimizedCandidates = f2lSolver.solveOptimizedCandidates(
                    copy(benchmarkCase.postCross()), latestProgress, fastTrace.algorithm(),
                    () -> System.nanoTime() >= deadlineNanos,
                    candidate -> {
                        long continuationStart = System.nanoTime();
                        var continuation = evaluateContinuation(
                                benchmarkCase.postCross(), benchmarkCase.crossMoveCount(),
                                candidate.algorithm(), ollSolver, pllSolver
                        );
                        evaluatedCandidates.incrementAndGet();
                        latestProgress.continuationNanos += System.nanoTime() - continuationStart;
                        if (continuation == null || !continuation.fullySolved()) return -1;
                        bestContinuation.updateAndGet(current -> current == null
                                || continuation.continuationMoves() < current.continuationMoves()
                                ? continuation : current);
                        return bestContinuation.get().continuationMoves();
                    },
                    fastContinuation != null && fastContinuation.fullySolved()
                            ? fastContinuation.continuationMoves() : Integer.MAX_VALUE
            );
            long optimizedNanos = System.nanoTime() - optimizedStart;
            long optimizedSearchNanos = Math.max(0, optimizedNanos - latestProgress.continuationNanos);
            int optimizedBestMoves = optimizedCandidates.stream()
                    .mapToInt(candidate -> candidate.algorithm().getMoveCount())
                    .min().orElse(fastTrace.algorithm().getMoveCount());
            int optimizedSolved = optimizedCandidates.stream()
                    .anyMatch(candidate -> candidate.trace().solved()) ? 1 : 0;
            optimizedSamples.add(new Sample(optimizedSearchNanos, optimizedBestMoves, optimizedSolved,
                    optimizedCandidates.size(), latestProgress.statesExplored,
                    latestProgress.statesPruned, latestProgress.duplicates,
                    latestProgress.lookupNanos(), latestProgress.continuationNanos));

            continuationSamples.add(new Sample(optimizedNanos,
                    bestContinuation.get() == null ? -1 : bestContinuation.get().fullCfopMoves(),
                    bestContinuation.get() != null && bestContinuation.get().fullySolved() ? 1 : 0,
                    evaluatedCandidates.get() + 1, latestProgress.statesExplored,
                    latestProgress.statesPruned, latestProgress.duplicates,
                    latestProgress.lookupNanos(), latestProgress.continuationNanos));

            System.out.printf("%3d/%d cross=%-1s fast=%4.1fms/%2dmoves optimized-search=%5.1fms/%2dmoves "
                            + "candidates=%2d full-search=%4.1fms/%2dmoves%n",
                    index + 1, SCRAMBLE_COUNT, benchmarkCase.crossFace(), millis(fastNanos),
                    fastTrace.algorithm().getMoveCount(), millis(optimizedSearchNanos), optimizedBestMoves,
                    optimizedCandidates.size(), millis(optimizedNanos),
                    bestContinuation.get() == null ? -1 : bestContinuation.get().fullCfopMoves());
        }

        runRegressionFixtures();

        System.out.println();
        System.out.println("F2L optimization benchmark (current production implementation)");
        System.out.printf("seed=%d scrambles=%d turns=%d cross-face-counts=%s%n", SEED,
                SCRAMBLE_COUNT, SCRAMBLE_LENGTH, faceCounts(cases));
        System.out.printf("cold solver initialization: %.2fms%n", millis(initializationNanos));
        if (OPTIMIZED_BUDGET_NANOS > 0) {
            System.out.printf("optimized search budget per scramble: %.2fs%n",
                    OPTIMIZED_BUDGET_NANOS / 1_000_000_000.0);
        }
        printSummary("Fast F2L", fastSamples);
        printSummary("Optimized F2L", optimizedSamples);
        printSummary("Best full CFOP solution (during optimized search)", continuationSamples);
        System.out.printf("  optimized continuation evaluation time: %.2fms total%n",
                millis(optimizedSamples.stream().mapToLong(Sample::continuationNanos).sum()));
        System.out.println("Times are informational; run on an otherwise idle machine for comparisons.");
    }

    private static List<BenchmarkCase> prepareCases() {
        var random = new Random(SEED);
        var crossSolver = new CrossSolver();
        var cases = new ArrayList<BenchmarkCase>(SCRAMBLE_COUNT);
        for (int index = 0; index < SCRAMBLE_COUNT; index++) {
            var scramble = randomScramble(random);
            var face = CROSS_FACES[index % CROSS_FACES.length];
            var scrambled = new CubeState();
            MoveApplier.applyAlgorithm(scrambled, scramble);
            var postCross = new OrientedCube(scrambled);
            var cross = crossSolver.solve(scrambled, face);
            postCross.applyMoves(cross.getMoves());
            cases.add(new BenchmarkCase("random-" + (index + 1), scramble, face,
                    cross.getMoveCount(), postCross));
        }
        return List.copyOf(cases);
    }

    private static void runRegressionFixtures() {
        var crossSolver = new CrossSolver();
        var fixtures = new ArrayList<BenchmarkCase>();
        fixtures.add(prepareFixture("recovery", "B' L B L2 D2 L D2", Face.D, crossSolver));
        fixtures.add(prepareFixture("rotated-frame",
                "U R2 B2 R' B R D F B' U2 D2 R2 B U2 R2 U2 L2 F' U2 L", Face.L, crossSolver));

        var random = new Random(SEED ^ 0xF2F2L);
        fixtures.add(findPairCountFixture(1, random, crossSolver));
        fixtures.add(findPairCountFixture(2, random, crossSolver));

        System.out.println("Regression fixture checks (outside the 100-scramble aggregate):");
        for (var fixture : fixtures) {
            var start = System.nanoTime();
            var fast = new F2LSolver().solveTrace(copy(fixture.postCross()));
            var optimized = new F2LSolver().solveOptimizedCandidates(
                    copy(fixture.postCross()), ignored -> { }, fast.algorithm()
            );
            System.out.printf("  %-18s cross=%s cross-moves=%d fast-f2l=%dmoves optimized-candidates=%d time=%.1fms%n",
                    fixture.label(), fixture.crossFace(), fixture.crossMoveCount(),
                    fast.algorithm().getMoveCount(), optimized.size(),
                    millis(System.nanoTime() - start));
        }
    }

    private static BenchmarkCase findPairCountFixture(int pairCount, Random random, CrossSolver crossSolver) {
        for (int attempt = 0; attempt < 10_000; attempt++) {
            var face = CROSS_FACES[attempt % CROSS_FACES.length];
            var scramble = randomScramble(random);
            var fixture = prepareFixture("already-" + pairCount + "-pairs", scramble, face, crossSolver);
            if (F2LAnalyzer.getSolvedSlots(
                    fixture.postCross().cubeState(), fixture.postCross().orientation()
            ).size() == pairCount) {
                return fixture;
            }
        }
        throw new IllegalStateException("Could not find deterministic fixture with " + pairCount + " solved pairs");
    }

    private static BenchmarkCase prepareFixture(String label, String scramble, Face face, CrossSolver crossSolver) {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, scramble);
        var postCross = new OrientedCube(scrambled);
        var cross = crossSolver.solve(scrambled, face);
        postCross.applyMoves(cross.getMoves());
        return new BenchmarkCase(label, scramble, face, cross.getMoveCount(), postCross);
    }

    private static String randomScramble(Random random) {
        var tokens = new ArrayList<String>(SCRAMBLE_LENGTH);
        Face previous = null;
        for (int turn = 0; turn < SCRAMBLE_LENGTH; turn++) {
            Face face;
            do {
                face = SCRAMBLE_FACES[random.nextInt(SCRAMBLE_FACES.length)];
            } while (face == previous);
            tokens.add(face + TURN_SUFFIXES[random.nextInt(TURN_SUFFIXES.length)]);
            previous = face;
        }
        return String.join(" ", tokens);
    }

    private static Continuation evaluateContinuation(
            OrientedCube postCross,
            int crossMoves,
            Algorithm f2l,
            OLLSolver ollSolver,
            PLLSolver pllSolver
    ) {
        try {
            var afterF2l = copy(postCross);
            afterF2l.applyMoves(f2l.getMoves());
            if (!solverF2lSolved(afterF2l)) {
                return null;
            }
            Continuation best = null;
            for (var oll : ollSolver.solveCandidates(afterF2l)) {
                var afterOll = copy(afterF2l);
                afterOll.applyMoves(oll.getMoves());
                var pll = pllSolver.solve(afterOll);
                afterOll.applyMoves(pll.getMoves());
                var continuation = new Continuation(
                        f2l.getMoveCount() + oll.getMoveCount() + pll.getMoveCount(),
                        crossMoves + f2l.getMoveCount() + oll.getMoveCount() + pll.getMoveCount(),
                        OLLAnalyzer.isOllSolved(afterOll.cubeState(), afterOll.orientation())
                                && PLLAnalyzer.isPllSolved(afterOll.cubeState(), afterOll.orientation())
                );
                if (best == null || continuation.continuationMoves() < best.continuationMoves()) {
                    best = continuation;
                }
            }
            return best;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    private static boolean solverF2lSolved(OrientedCube cube) {
        return cfop.F2LAnalyzer.isF2LSolved(cube.cubeState(), cube.orientation());
    }

    private static OrientedCube copy(OrientedCube cube) {
        return new OrientedCube(cube.cubeState().copy(), cube.orientation());
    }

    private static String faceCounts(List<BenchmarkCase> cases) {
        var counts = new java.util.EnumMap<Face, Integer>(Face.class);
        for (var face : CROSS_FACES) counts.put(face, 0);
        cases.forEach(item -> counts.compute(item.crossFace(), (ignored, count) -> count + 1));
        return counts.toString();
    }

    private static void printSummary(String label, List<Sample> samples) {
        var times = samples.stream().mapToLong(Sample::elapsedNanos).sorted().toArray();
        var moves = samples.stream().mapToInt(Sample::moves).filter(value -> value >= 0).toArray();
        var completionCount = samples.stream().mapToInt(Sample::completed).sum();
        var candidates = samples.stream().mapToInt(Sample::candidateCount).sum();
        var explored = samples.stream().mapToLong(Sample::statesExplored).sum();
        var pruned = samples.stream().mapToLong(Sample::statesPruned).sum();
        var duplicates = samples.stream().mapToLong(Sample::duplicates).sum();
        var lookupNanos = samples.stream().mapToLong(Sample::lookupNanos).sum();
        System.out.printf("%s%n", label);
        System.out.printf("  time ms: mean=%.2f median=%.2f p95=%.2f%n", meanMillis(times),
                millis(percentile(times, 0.50)), millis(percentile(times, 0.95)));
        System.out.printf("  moves: mean=%.2f completed=%d/%d candidate-count=%d%n",
                mean(moves), completionCount, samples.size(), candidates);
        System.out.printf("  search: states=%d pruned=%d duplicates=%d lookup-ms=%.2f%n",
                explored, pruned, duplicates, millis(lookupNanos));
    }

    private static long percentile(long[] sorted, double percentile) {
        return sorted[Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1)];
    }

    private static double meanMillis(long[] nanos) {
        return nanos.length == 0 ? 0 : nanosMean(nanos) / 1_000_000.0;
    }

    private static double nanosMean(long[] nanos) {
        long total = 0;
        for (long value : nanos) total += value;
        return total / (double) nanos.length;
    }

    private static double mean(int[] values) {
        if (values.length == 0) return 0;
        long sum = 0;
        for (int value : values) sum += value;
        return sum / (double) values.length;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record BenchmarkCase(
            String label,
            String scramble,
            Face crossFace,
            int crossMoveCount,
            OrientedCube postCross
    ) {
    }

    private record Continuation(int continuationMoves, int fullCfopMoves, boolean fullySolved) {
    }

    private record Sample(
            long elapsedNanos,
            int moves,
            int completed,
            int candidateCount,
            long statesExplored,
            long statesPruned,
            long duplicates,
            long lookupNanos,
            long continuationNanos
    ) {
    }

    private static final class ProgressAccumulator implements Consumer<F2LSolver.F2LSearchProgress> {
        private long statesExplored;
        private long statesPruned;
        private long duplicates;
        private long insertLookupNanos;
        private long setupLookupNanos;
        private long validationNanos;
        private long continuationNanos;

        @Override
        public void accept(F2LSolver.F2LSearchProgress progress) {
            statesExplored = Math.max(statesExplored, progress.statesExplored());
            statesPruned = Math.max(statesPruned, progress.statesPruned());
            duplicates = Math.max(duplicates, progress.duplicateStates());
            insertLookupNanos = Math.max(insertLookupNanos, progress.insertLookupNanos());
            setupLookupNanos = Math.max(setupLookupNanos, progress.setupLookupNanos());
            validationNanos = Math.max(validationNanos, progress.validationNanos());
        }

        private long lookupNanos() {
            return insertLookupNanos + setupLookupNanos + validationNanos;
        }
    }
}
