package com.example.rag.jobs;

import com.example.rag.jobs.mail.EmailMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

/**
 * Pulls job postings out of a LinkedIn job-alert email. The alert markup is a table of cards, each
 * one anchored by a link to /jobs/view/{id}, so the job id is the reliable key and the surrounding
 * text lines give the title, company and location.
 */
@Component
public class LinkedInJobParser {

    private static final Pattern JOB_ID = Pattern.compile("/jobs/view/(\\d+)");
    private static final Pattern TRACKED_LINK = Pattern.compile("https?://[^\\s\"'<>]*?/jobs/view/\\d+[^\\s\"'<>]*");

    /** Lines that appear inside job cards but say nothing about the job itself. */
    private static final List<Pattern> NOISE = List.of(
            Pattern.compile("(?i)^(see all jobs?|view job|apply now|see more jobs?|unsubscribe)\\b.*"),
            Pattern.compile("(?i)^(promoted|actively recruiting|easy apply|be an early applicant)$"),
            Pattern.compile("(?i)^\\d+\\s+(minute|hour|day|week|month)s?\\s+ago$"),
            Pattern.compile("(?i)^\\d+\\s+(new\\s+)?jobs?\\b.*"),
            Pattern.compile("(?i)^(your job alert|new jobs? for you)\\b.*"));

    public List<JobPosting> parse(EmailMessage email) {
        String html = email.htmlBody();
//        if (html != null && !html.isBlank()) {
//            return parseHtml(html);
//        }
        return parsePlainText(email.textBody());
    }

    private List<JobPosting> parseHtml(String html) {
        Document document = Jsoup.parse(html);
        Map<String, JobPosting> byId = new LinkedHashMap<>();

        for (Element anchor : document.select("a[href*=/jobs/view/]")) {
            String jobId = jobId(anchor.attr("href"));
            String title = clean(anchor.text());
            if (jobId == null || title.isEmpty() || isNoise(title)) {
                continue;
            }
            // Alerts repeat the same job as an image link and a text link; the first titled one wins.
            if (byId.containsKey(jobId)) {
                continue;
            }
            byId.put(jobId, buildPosting(jobId, title, cardLines(anchor, title)));
        }
        return List.copyOf(byId.values());
    }

    /** Fallback for text-only alerts: recover the job ids from raw links, with no card structure to read. */
    private List<JobPosting> parsePlainText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<String, JobPosting> byId = new LinkedHashMap<>();
        Matcher matcher = TRACKED_LINK.matcher(text);
        while (matcher.find()) {
            String jobId = jobId(matcher.group());
            if (jobId != null) {
                byId.putIfAbsent(jobId, new JobPosting(jobId, "Unknown role", "", "", applyUrl(jobId), "", ""));
            }
        }
        return List.copyOf(byId.values());
    }

    private static JobPosting buildPosting(String jobId, String title, List<String> lines) {
        String company = lines.size() > 0 ? lines.get(0) : "";
        String location = lines.size() > 1 ? lines.get(1) : "";
        String snippet = String.join(" | ", lines);
        return new JobPosting(jobId, title, company, location, applyUrl(jobId), snippet, "");
    }

    /** Text of the card holding this link, minus the title and the boilerplate, in document order. */
    private static List<String> cardLines(Element anchor, String title) {
        Element card = anchor.closest("td");
        if (card == null) {
            card = anchor.parent();
        }
        if (card == null) {
            return List.of();
        }

        // Turn visual breaks into newlines before reading wholeText(), which preserves them.
        Element clone = card.clone();
        clone.select("br").forEach(br -> br.replaceWith(new TextNode("\n")));
        clone.select("p, div, li, tr").forEach(block -> block.appendChild(new TextNode("\n")));

        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (String raw : clone.wholeText().split("\\R")) {
            String line = clean(raw);
            if (!line.isEmpty() && !line.equalsIgnoreCase(title) && !isNoise(line)) {
                lines.add(line);
            }
        }
        return new ArrayList<>(lines);
    }

    private static String jobId(String href) {
        Matcher matcher = JOB_ID.matcher(href);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** The tracked link in the mail expires, so the report points at the canonical posting. */
    private static String applyUrl(String jobId) {
        return "https://www.linkedin.com/jobs/view/" + jobId + "/";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isNoise(String line) {
        String candidate = line.toLowerCase(Locale.ROOT);
        if (candidate.length() < 2) {
            return true;
        }
        return NOISE.stream().anyMatch(pattern -> pattern.matcher(line).matches());
    }
}
