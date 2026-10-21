package database.persistence.entity;

import database.persistence.repository.UserJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import server.solve.SolverProfileOperations;
import server.solve.SolverProfileService;

import java.util.List;

/** Stores only canonical algorithm references; algorithm definitions remain in versioned JSON. */
@Service
public class SpringSolverProfilePersistenceService implements SolverProfileOperations {
    private final UserJpaRepository users;
    private final JdbcTemplate jdbc;
    private final SolverProfileService profiles;

    public SpringSolverProfilePersistenceService(
            UserJpaRepository users,
            JdbcTemplate jdbc,
            SolverProfileService profiles
    ) {
        this.users = users;
        this.jdbc = jdbc;
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findDisabledAlgorithms(String userExternalId) {
        var user = requireUser(userExternalId);
        return jdbc.query("""
                        SELECT phase, case_name
                        FROM user_disabled_algorithms
                        WHERE user_id = ?
                        ORDER BY phase, case_name
                        """,
                (row, index) -> row.getString("phase") + ":" + row.getString("case_name"), user.getId());
    }

    @Override
    @Transactional
    public List<String> replaceDisabledAlgorithms(String userExternalId, List<String> disabledAlgorithms) {
        var user = requireUser(userExternalId);
        var validated = profiles.validate(disabledAlgorithms);
        jdbc.update("DELETE FROM user_disabled_algorithms WHERE user_id = ?", user.getId());
        if (!validated.isEmpty()) {
            jdbc.batchUpdate("""
                            INSERT INTO user_disabled_algorithms (user_id, phase, case_name)
                            VALUES (?, ?, ?)
                            """,
                    validated,
                    validated.size(),
                    (statement, id) -> {
                        var separator = id.indexOf(':');
                        statement.setLong(1, user.getId());
                        statement.setString(2, id.substring(0, separator));
                        statement.setString(3, id.substring(separator + 1));
                    });
        }
        return validated;
    }

    private UserEntity requireUser(String externalId) {
        return users.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
