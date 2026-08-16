package com.example.rag.web;

import com.example.rag.jobs.mail.InboxAlertMailMover;
import com.example.rag.jobs.mail.InboxAlertMailMover.BatchMoveResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** IMAP helpers that do not require Postgres / indexing. */
@RestController
public class JobMailController {

    private final InboxAlertMailMover mailMover;

    public JobMailController(InboxAlertMailMover mailMover) {
        this.mailMover = mailMover;
    }

    /**
     * Move LinkedIn and Glassdoor job-alert emails from INBOX into their folders.
     *
     * <p>{@code POST http://localhost:8000/jobs/mail/move-alerts}
     */
    @PostMapping(path = "/jobs/mail/move-alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    public BatchMoveResult moveAlerts() {
        return mailMover.moveAll();
    }
}
