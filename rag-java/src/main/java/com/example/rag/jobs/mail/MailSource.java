package com.example.rag.jobs.mail;

import java.time.Instant;
import java.util.List;

/** Where job-alert mail comes from. Implemented by a live IMAP mailbox and by a folder of .eml files. */
public interface MailSource {

    /**
     * @param sender only messages from this address are returned
     * @param since  only messages received at this instant or later are returned
     */
    List<EmailMessage> fetch(String sender, Instant since, int maxEmails);

    String describe();
}
