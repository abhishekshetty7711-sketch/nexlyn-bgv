package com.nexlyn.bgv.cases.internal.web;

import com.nexlyn.bgv.cases.internal.service.DashboardService;
import com.nexlyn.bgv.cases.internal.service.DashboardService.Dashboard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The dashboard numbers and lists for the signed-in admin. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public ResponseEntity<Dashboard> get() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(dashboard.dashboard());
    }
}
