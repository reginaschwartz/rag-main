package com.example.rag;

import com.example.rag.cli.CliMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(RagApplication.class);
        if (CliMode.isRequested(args)) {
            // The batch/query/jobs scripts are one-shot commands, so no HTTP server is started for them.
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        if (CliMode.skipsDatabase(args)) {
            // Job scanning / mail move only need mail (+ OpenAI for jobs); skip Postgres wiring.
            System.setProperty("rag.database.enabled", "false");
            System.setProperty("spring.autoconfigure.exclude",
                    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration");
        }
        application.run(args);
    }
}
