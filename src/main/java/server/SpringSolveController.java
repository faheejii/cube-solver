package server;

import api.SolveApiRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for the synchronous solve endpoint. */
@RestController
@RequestMapping("/api/solve")
final class SpringSolveController {
    private final SolveJobManager jobManager;

    SpringSolveController(SolveJobManager jobManager) {
        this.jobManager = jobManager;
    }

    @PostMapping
    ResponseEntity<String> solve(@RequestBody SolveApiRequest request) throws Exception {
        var job = jobManager.submit(request, null, null, false);
        while (true) {
            var snapshot = jobManager.find(job.id());
            if ("completed".equals(snapshot.status()) && snapshot.result() != null) {
                return SpringRequestSupport.json(200, JsonSupport.solveResultJson(snapshot.result()));
            }
            if ("timed_out".equals(snapshot.status())) {
                return SpringRequestSupport.error(504, snapshot.error());
            }
            if ("failed".equals(snapshot.status())) {
                return SpringRequestSupport.error(500, snapshot.error());
            }
            if ("cancelled".equals(snapshot.status())) {
                return SpringRequestSupport.error(409, snapshot.error());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                jobManager.cancel(job.id());
                throw new java.io.IOException("Solve request interrupted", exception);
            }
        }
    }
}
