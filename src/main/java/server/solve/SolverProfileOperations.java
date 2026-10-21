package server.solve;

import java.util.List;

/** Database operations for an administrator's personal solver profile. */
public interface SolverProfileOperations {
    List<String> findDisabledAlgorithms(String userExternalId);

    List<String> replaceDisabledAlgorithms(String userExternalId, List<String> disabledAlgorithms);
}
