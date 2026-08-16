package com.example.rag.jobs.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.rag.config.JobAgentProperties;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import java.util.List;
import org.junit.jupiter.api.Test;

class InboxAlertMailMoverTest {

    @Test
    void parseAlertSenders_defaultsToBothLinkedInAddresses() {
        assertEquals(
                List.of("jobalerts-noreply@linkedin.com", "jobs-noreply@linkedin.com"),
                JobAgentProperties.parseAlertSenders(null));
    }

    @Test
    void parseGlassdoorSenders_defaultsToNoreply() {
        assertEquals(
                List.of("noreply@glassdoor.com"),
                JobAgentProperties.parseGlassdoorSenders(null));
        assertEquals(
                List.of("noreply@glassdoor.com"),
                JobAgentProperties.parseGlassdoorSenders("  "));
    }

    @Test
    void parseAlertSenders_splitsAndDedupes() {
        assertEquals(
                List.of("jobalerts-noreply@linkedin.com", "jobs-noreply@linkedin.com"),
                JobAgentProperties.parseAlertSenders(
                        "jobalerts-noreply@linkedin.com, jobs-noreply@linkedin.com; JOBALERTS-NOREPLY@LINKEDIN.COM"));
    }

    @Test
    void senderTerm_usesOrForMultipleSenders() {
        SearchTerm term = InboxAlertMailMover.senderTerm(
                List.of("jobalerts-noreply@linkedin.com", "jobs-noreply@linkedin.com"));
        assertInstanceOf(OrTerm.class, term);
    }

    @Test
    void senderTerm_usesFromForSingleSender() {
        SearchTerm term = InboxAlertMailMover.senderTerm(List.of("noreply@glassdoor.com"));
        assertInstanceOf(FromStringTerm.class, term);
    }

    @Test
    void senderTerm_rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> InboxAlertMailMover.senderTerm(List.of()));
    }
}
