package server;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Spring-owned bounded executors for fast and optimized solve work. */
@Configuration(proxyBeanMethods = false)
class SpringSolveJobExecutorConfiguration {
    @Bean(name = "fastSolveExecutor", destroyMethod = "shutdownNow")
    @Qualifier("fastSolveExecutor")
    ExecutorService fastSolveExecutor(
            @Value("${server.fast.workers:2}") int workers,
            @Value("${server.fast.queue:16}") int queueSize
    ) {
        return boundedExecutor("fast-solve-worker", workers, queueSize);
    }

    @Bean(name = "optimizedSolveExecutor", destroyMethod = "shutdownNow")
    @Qualifier("optimizedSolveExecutor")
    ExecutorService optimizedSolveExecutor(
            @Value("${server.optimized.workers:1}") int workers,
            @Value("${server.optimized.queue:4}") int queueSize
    ) {
        return boundedExecutor("optimized-solve-worker", workers, queueSize);
    }

    private static ExecutorService boundedExecutor(String threadName, int workers, int queueSize) {
        int normalizedWorkers = Math.max(1, workers);
        int normalizedQueueSize = Math.max(1, queueSize);
        ThreadFactory threadFactory = runnable -> {
            var thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                normalizedWorkers,
                normalizedWorkers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(normalizedQueueSize),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
