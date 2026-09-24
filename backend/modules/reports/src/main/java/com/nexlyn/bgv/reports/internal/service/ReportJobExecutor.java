package com.nexlyn.bgv.reports.internal.service;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.reports.internal.config.ReportsProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Runs report jobs on a small, bounded set of threads (CLAUDE.md sections 4.2 rule 7 and 13): a few
 * browsers at a time, a limited queue behind them, and a plain "busy" answer when the queue is full.
 * Rendering can never take over the web threads or the machine's memory.
 */
@Component
public class ReportJobExecutor {

    private final ThreadPoolExecutor pool;

    public ReportJobExecutor(ReportsProperties properties) {
        int threads = properties.concurrentRenders();
        this.pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queue()), runnable -> {
            Thread thread = new Thread(runnable, "report-render");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Queues a job; refuses with a plain message when too many are waiting. */
    public void submit(Runnable job) {
        try {
            pool.execute(job);
        } catch (RejectedExecutionException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Many reports are being made right now. Please try again in a minute.");
        }
    }

    @PreDestroy
    void stop() {
        pool.shutdown();
    }
}
