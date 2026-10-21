package server.solve;

import database.persistence.entity.SpringHistoryPersistenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import solver.CfopSolveService;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

@Configuration(proxyBeanMethods = false)
class SpringSolveJobConfiguration {
    @Bean
    CompletedSolutionPersistence completedSolutionPersistence(
            SpringHistoryPersistenceService history,
            ObjectProvider<DataSource> dataSources
    ) {
        return new SpringCompletedSolutionPersistence(history, dataSources.getIfAvailable());
    }

    @Bean(destroyMethod = "close")
    SolveJobOperations solveJobOperations(
            CfopSolveService solveService,
            CompletedSolutionPersistence persistence,
            SolveJobMetrics metrics,
            SolveJobSaveCommandFactory saveCommandFactory,
            SolverProfileService solverProfiles,
            @Qualifier("optimizedSolveExecutor") ExecutorService optimizedExecutor,
            @Qualifier("fastSolveExecutor") ExecutorService fastExecutor
    ) {
        return new SolveJobManager(
                solveService, persistence, metrics, saveCommandFactory, optimizedExecutor, fastExecutor, solverProfiles);
    }
}
