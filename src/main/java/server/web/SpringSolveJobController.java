package server.web;

import api.CreateSolveJobRequest;
import server.solve.SolveJobOperations;
import server.solve.SolverProfileOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for asynchronous solve jobs and their existing ownership rules. */
@RestController
@RequestMapping("/api/solve-jobs")
final class SpringSolveJobController {
    private final SolveJobOperations jobManager;
    private final SolverProfileOperations solverProfiles;

    @Autowired
    SpringSolveJobController(SolveJobOperations jobManager, SolverProfileOperations solverProfiles) {
        this.jobManager = jobManager;
        this.solverProfiles = solverProfiles;
    }

    SpringSolveJobController(SolveJobOperations jobManager) {
        this(jobManager, null);
    }

    @PostMapping
    ResponseEntity<String> create(@RequestBody CreateSolveJobRequest request) throws Exception {
        var user = SpringRequestSupport.currentUser();
        if (request.saveOnComplete() && user == null) {
            throw new server.auth.AuthOperations.UnauthorizedException("Authentication required");
        }
        var disabledAlgorithms = user != null && user.isAdmin() && solverProfiles != null
                ? solverProfiles.findDisabledAlgorithms(user.externalId())
                : java.util.List.<String>of();
        var job = disabledAlgorithms.isEmpty()
                ? jobManager.submit(
                        request.solveRequest(),
                        user == null ? null : user.externalId(),
                        request.solveId(),
                        request.saveOnComplete()
                )
                : jobManager.submit(
                        request.solveRequest(),
                        user.externalId(),
                        request.solveId(),
                        request.saveOnComplete(),
                        disabledAlgorithms
                );
        return SpringRequestSupport.json(202, JsonSupport.solveJobJson(job));
    }

    @GetMapping("/{jobId}")
    ResponseEntity<String> find(@PathVariable("jobId") String jobId) throws Exception {
        var user = SpringRequestSupport.currentUser();
        var job = jobManager.find(jobId, user == null ? null : user.externalId());
        return SpringRequestSupport.json(200, JsonSupport.solveJobJson(job));
    }

    @DeleteMapping("/{jobId}")
    ResponseEntity<String> cancel(@PathVariable("jobId") String jobId) throws Exception {
        var user = SpringRequestSupport.currentUser();
        var job = jobManager.cancel(jobId, user == null ? null : user.externalId());
        return SpringRequestSupport.json(200, JsonSupport.solveJobJson(job));
    }
}
