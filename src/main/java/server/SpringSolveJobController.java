package server;

import api.CreateSolveJobRequest;
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
    private final SolveJobManager jobManager;

    SpringSolveJobController(SolveJobManager jobManager) {
        this.jobManager = jobManager;
    }

    @PostMapping
    ResponseEntity<String> create(@RequestBody String body) throws Exception {
        var json = SpringRequestSupport.requireJson(body);
        var request = new CreateSolveJobRequest(
                JsonSupport.readString(json, "scramble"),
                JsonSupport.readString(json, "crossFace"),
                JsonSupport.readString(json, "f2lMode"),
                JsonSupport.readLong(json, "solveId"),
                JsonSupport.readBoolean(json, "saveOnComplete"),
                JsonSupport.readLong(json, "deadlineSeconds"),
                JsonSupport.readBoolean(json, "deepColorNeutral")
        );
        var user = SpringRequestSupport.currentUser();
        if (request.saveOnComplete() && user == null) {
            throw new AuthService.UnauthorizedException("Authentication required");
        }
        var job = jobManager.submit(
                request.solveRequest(),
                user == null ? null : user.externalId(),
                request.solveId(),
                request.saveOnComplete()
        );
        return SpringRequestSupport.json(202, JsonSupport.solveJobJson(job));
    }

    @GetMapping("/{jobId}")
    ResponseEntity<String> find(@PathVariable String jobId) throws Exception {
        var user = SpringRequestSupport.currentUser();
        var job = jobManager.find(jobId, user == null ? null : user.externalId());
        return SpringRequestSupport.json(200, JsonSupport.solveJobJson(job));
    }

    @DeleteMapping("/{jobId}")
    ResponseEntity<String> cancel(@PathVariable String jobId) throws Exception {
        var user = SpringRequestSupport.currentUser();
        var job = jobManager.cancel(jobId, user == null ? null : user.externalId());
        return SpringRequestSupport.json(200, JsonSupport.solveJobJson(job));
    }
}
