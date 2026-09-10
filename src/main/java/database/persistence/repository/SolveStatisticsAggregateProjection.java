package database.persistence.repository;

import java.math.BigDecimal;

public interface SolveStatisticsAggregateProjection {
    Integer getSolveCount();
    Integer getDnfCount();
    Integer getBestMs();
    BigDecimal getAverageMs();
}
