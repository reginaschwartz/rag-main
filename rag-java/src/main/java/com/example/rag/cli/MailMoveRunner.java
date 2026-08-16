package com.example.rag.cli;

import com.example.rag.jobs.mail.InboxAlertMailMover;
import com.example.rag.jobs.mail.InboxAlertMailMover.BatchMoveResult;
import com.example.rag.jobs.mail.InboxAlertMailMover.MoveResult;
import com.example.rag.jobs.mail.InboxAlertMailMover.MovedMessage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot IMAP move of LinkedIn + Glassdoor alerts into their folders:
 * {@code java -jar rag-api.jar --rag.cli=mail-move}.
 */
@Component
@ConditionalOnProperty(name = CliMode.PROPERTY, havingValue = CliMode.MAIL_MOVE)
public class MailMoveRunner implements ApplicationRunner {

    private final InboxAlertMailMover mover;

    public MailMoveRunner(InboxAlertMailMover mover) {
        this.mover = mover;
    }

    @Override
    public void run(ApplicationArguments args) {
        BatchMoveResult batch = mover.moveAll();
        System.out.printf("Moved %d message(s) total from %s%n", batch.totalMoved(), batch.sourceFolder());
        for (MoveResult result : batch.results()) {
            System.out.printf(
                    "  %s → %s: %d%n",
                    String.join(", ", result.senders()),
                    result.targetFolder(),
                    result.movedCount());
            for (MovedMessage message : result.messages()) {
                System.out.printf("    - %s | %s%n", message.from(), message.subject());
            }
        }
    }
}
