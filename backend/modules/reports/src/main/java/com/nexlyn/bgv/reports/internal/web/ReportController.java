package com.nexlyn.bgv.reports.internal.web;

import com.nexlyn.bgv.reports.internal.service.ReportService;
import com.nexlyn.bgv.reports.internal.service.ReportViews.Download;
import com.nexlyn.bgv.reports.internal.service.ReportViews.JobView;
import com.nexlyn.bgv.reports.internal.service.ReportViews.VersionView;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Reports (CLAUDE.md section 9.4). Thin: the rules live in {@link ReportService}. */
@RestController
@RequestMapping("/api/cases/{id}/reports")
public class ReportController {

    record GenerateRequest(boolean acknowledgeWarnings) {
    }

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /**
     * The report as HTML. Locked down so that whatever is in it can only show itself: no scripts, no
     * network, no framing by other sites; the page is shown in a sandboxed frame by the frontend.
     */
    @GetMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .header("Content-Security-Policy", "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(reports.preview(id));
    }

    @PostMapping
    public ResponseEntity<JobView> generate(@PathVariable UUID id, @RequestBody(required = false) GenerateRequest body) {
        boolean acknowledged = body != null && body.acknowledgeWarnings();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reports.requestDraft(id, acknowledged));
    }

    @GetMapping("/jobs/{jobId}")
    public JobView job(@PathVariable UUID id, @PathVariable UUID jobId) {
        return reports.job(id, jobId);
    }

    @GetMapping
    public List<VersionView> versions(@PathVariable UUID id) {
        return reports.versions(id);
    }

    @GetMapping("/{version}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable int version) {
        Download file = reports.download(id, version);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.bytes());
    }
}
