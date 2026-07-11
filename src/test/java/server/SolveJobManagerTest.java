package server;

import api.SolveApiRequest;
import database.DatabaseConfig;
import database.DatabaseManager;
import org.junit.jupiter.api.Test;
import solver.CfopSolveRequest;
import solver.CfopSolveResult;
import solver.CfopSolveService;
import solver.CfopStageResult;
import solver.SolveCancellation;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SolveJobManagerTest {
    @Test
    void fastJob_shouldCompleteWhileOptimizedWorkerIsBusy() throws Exception {
        var optimizedRelease = new CountDownLatch(1);
        var service = new ControlledSolveService(optimizedRelease);
        var manager = manager(service);

        var optimized = manager.submit(
                new SolveApiRequest("R", "U", "optimized"),
                null,
                null,
                false
        );
        waitForStatus(manager, optimized.id(), "running");

        var fast = manager.submit(
                new SolveApiRequest("U", "U", "greedy"),
                null,
                null,
                false
        );

        assertEquals("completed", waitForStatus(manager, fast.id(), "completed").status());
        assertEquals("running", manager.find(optimized.id()).status());
        optimizedRelease.countDown();
        waitForStatus(manager, optimized.id(), "completed");
    }

    @Test
    void cancel_shouldRemoveQueuedJobWithoutRunningIt() throws Exception {
        var optimizedRelease = new CountDownLatch(1);
        var service = new ControlledSolveService(optimizedRelease);
        var manager = manager(service);

        var running = manager.submit(
                new SolveApiRequest("R", "U", "optimized"),
                null,
                null,
                false
        );
        waitForStatus(manager, running.id(), "running");
        var queued = manager.submit(
                new SolveApiRequest("U", "U", "optimized"),
                null,
                null,
                false
        );

        assertEquals("cancelled", manager.cancel(queued.id()).status());
        optimizedRelease.countDown();
        waitForStatus(manager, running.id(), "completed");
        Thread.sleep(50);

        assertEquals("cancelled", manager.find(queued.id()).status());
        assertEquals(1, service.optimizedInvocations.get());
    }

    @Test
    void cancel_shouldInterruptRunningJobAndRemainCancelled() throws Exception {
        var optimizedRelease = new CountDownLatch(1);
        var manager = manager(new ControlledSolveService(optimizedRelease));
        var job = manager.submit(
                new SolveApiRequest("R", "U", "optimized"),
                null,
                null,
                false
        );
        waitForStatus(manager, job.id(), "running");

        assertEquals("cancelled", manager.cancel(job.id()).status());
        assertEquals("cancelled", waitForStatus(manager, job.id(), "cancelled").status());
        optimizedRelease.countDown();
        Thread.sleep(50);

        assertEquals("cancelled", manager.find(job.id()).status());
        assertEquals("cancelled", manager.cancel(job.id()).status());
    }

    @Test
    void cancelRunningOptimizedJob_shouldReleaseWorkerForQueuedOptimizedJob() throws Exception {
        var optimizedRelease = new CountDownLatch(1);
        var service = new ControlledSolveService(optimizedRelease);
        var manager = manager(service);
        var running = manager.submit(
                new SolveApiRequest("R", "U", "optimized"),
                null,
                null,
                false
        );
        waitForStatus(manager, running.id(), "running");
        var queued = manager.submit(
                new SolveApiRequest("U", "COLOR_NEUTRAL", "optimized"),
                null,
                null,
                false
        );

        assertEquals("cancelled", manager.cancel(running.id()).status());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (service.optimizedInvocations.get() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(2, service.optimizedInvocations.get());
        optimizedRelease.countDown();

        assertEquals("completed", waitForStatus(manager, queued.id(), "completed").status());
    }

    @Test
    void cancelRealOptimizedSolve_shouldReleaseWorkerForQueuedSolve() throws Exception {
        var manager = manager(new CfopSolveService());
        var running = manager.submit(
                new SolveApiRequest(
                        "B R' F U D' R D' R2 B U2 R U2 L' D2 R F2 R2 D2 R B2 R2",
                        "R",
                        "optimized"
                ),
                null,
                null,
                false
        );
        waitForStatus(manager, running.id(), "running");
        var queued = manager.submit(
                new SolveApiRequest("R U R'", "COLOR_NEUTRAL", "optimized"),
                null,
                null,
                false
        );

        manager.cancel(running.id());
        var released = waitUntilNotQueued(manager, queued.id());

        assertNotEquals("queued", released.status());
        manager.cancel(queued.id());
    }

    @Test
    void cancelLinkedJobs_shouldOnlyCancelMatchingSaveJob() throws Exception {
        var optimizedRelease = new CountDownLatch(1);
        var service = new ControlledSolveService(optimizedRelease);
        var manager = new SolveJobManager(
                service,
                new DatabaseManager(new DatabaseConfig(
                        true,
                        "jdbc:postgresql://invalid/test",
                        "test",
                        "test"
                ))
        );
        var linked = manager.submit(
                new SolveApiRequest("R", "U", "optimized"),
                "user-a",
                42L,
                true
        );
        waitForStatus(manager, linked.id(), "running");
        var unrelated = manager.submit(
                new SolveApiRequest("U", "U", "optimized"),
                "user-a",
                43L,
                true
        );

        manager.cancelLinkedJobs("user-a", 42L);

        assertEquals("cancelled", manager.find(linked.id()).status());
        assertNotEquals("cancelled", manager.find(unrelated.id()).status());
        manager.cancel(unrelated.id());
        optimizedRelease.countDown();
    }

    private static SolveJobManager manager(CfopSolveService service) {
        return new SolveJobManager(
                service,
                new DatabaseManager(DatabaseConfig.disabled())
        );
    }

    private static SolveJobManager.JobSnapshot waitForStatus(
            SolveJobManager manager,
            String jobId,
            String expected
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var snapshot = manager.find(jobId);
            if (expected.equals(snapshot.status())) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Job did not reach status " + expected);
    }

    private static SolveJobManager.JobSnapshot waitUntilNotQueued(
            SolveJobManager manager,
            String jobId
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var snapshot = manager.find(jobId);
            if (!"queued".equals(snapshot.status())) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Queued job did not acquire the optimized worker");
    }

    private static final class ControlledSolveService extends CfopSolveService {
        private final CountDownLatch optimizedRelease;
        private final AtomicInteger optimizedInvocations = new AtomicInteger();

        private ControlledSolveService(CountDownLatch optimizedRelease) {
            this.optimizedRelease = optimizedRelease;
        }

        @Override
        public CfopSolveResult solveWithProgress(
                CfopSolveRequest request,
                Consumer<solver.F2LSolver.F2LSearchProgress> optimizedProgressListener
        ) {
            if (request.f2lMode() == solver.F2LMode.OPTIMIZED) {
                optimizedInvocations.incrementAndGet();
                optimizedProgressListener.accept(new solver.F2LSolver.F2LSearchProgress(
                        3, 1, 1, 10, 2, 0, 18
                ));
                while (optimizedRelease.getCount() > 0) {
                    SolveCancellation.throwIfCancelled();
                    try {
                        optimizedRelease.await(10, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        SolveCancellation.throwIfCancelled();
                    }
                }
            }
            var solved = new CfopStageResult("stage", "", 0, true, "ok");
            return new CfopSolveResult(
                    request.scramble(),
                    "U",
                    request.f2lMode().apiValue(),
                    0,
                    0,
                    solved,
                    solved,
                    solved,
                    solved,
                    "[FR, FL, BL, BR]",
                    true,
                    1.0
            );
        }
    }
}
