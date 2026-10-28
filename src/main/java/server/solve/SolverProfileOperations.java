package server.solve;

import java.util.List;
import java.util.UUID;

/** Database operations for an administrator's personal solver profile. */
public interface SolverProfileOperations {
    List<String> findDisabledAlgorithms(String userExternalId);

    List<String> replaceDisabledAlgorithms(String userExternalId, List<String> disabledAlgorithms);

    default Profile findProfile(String userExternalId) {
        return new Profile(findDisabledAlgorithms(userExternalId), List.of(), List.of(), List.of());
    }

    default Profile replaceProfile(String userExternalId, List<String> disabledAlgorithms,
                                   List<UUID> disabledAlgorithmIds, List<UUID> enabledTrialRevisionIds) {
        return new Profile(replaceDisabledAlgorithms(userExternalId, disabledAlgorithms), List.of(), List.of(), List.of());
    }

    record Profile(List<String> disabledAlgorithms, List<UUID> disabledAlgorithmIds,
                   List<UUID> enabledTrialRevisionIds, List<UUID> inactiveTrialRevisionIds) {
        public Profile {
            disabledAlgorithms = List.copyOf(disabledAlgorithms);
            disabledAlgorithmIds = List.copyOf(disabledAlgorithmIds);
            enabledTrialRevisionIds = List.copyOf(enabledTrialRevisionIds);
            inactiveTrialRevisionIds = List.copyOf(inactiveTrialRevisionIds);
        }
    }
}
