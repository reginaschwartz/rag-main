package com.example.rag.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class LinkedInJobDescriptionFetcherTest {

    @Test
    void extractViewJobUrlFromSnippet() {
        String snippet = """
                Senior Java Backend Engineer | Acme Cloud Ltd | Tel Aviv
                View job: https://www.linkedin.com/jobs/view/4440025121/?refId=abc
                """;

        assertThat(LinkedInJobDescriptionFetcher.extractViewJobUrl(snippet, "4440025121"))
                .contains("https://www.linkedin.com/jobs/view/4440025121/?refId=abc");
    }

    @Test
    void extractViewJobUrlPicksMatchingJobId() {
        String snippet = """
                View job: https://www.linkedin.com/jobs/view/111/
                View job: https://www.linkedin.com/jobs/view/222/
                """;

        assertThat(LinkedInJobDescriptionFetcher.extractViewJobUrl(snippet, "222"))
                .contains("https://www.linkedin.com/jobs/view/222/");
    }

    @Test
    void extractViewJobUrlIgnoresTrailingPunctuation() {
        String snippet = "View job: https://www.linkedin.com/jobs/view/1/).";

        assertThat(LinkedInJobDescriptionFetcher.extractViewJobUrl(snippet, "1"))
                .contains("https://www.linkedin.com/jobs/view/1/");
    }

    @Test
    void parseAboutTheJobFromLinkedInHtml() throws Exception {
        String html = new String(
                getClass().getResourceAsStream("/linkedin-job-page.html").readAllBytes(),
                StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html);

        String description = LinkedInJobDescriptionFetcher.parseAboutTheJob(document);

        assertThat(description).contains("Who are we?");
        assertThat(description).contains("Java engineer with Spring Boot");
    }
}
