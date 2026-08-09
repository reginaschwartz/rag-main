package com.example.rag.jobs;

/** A single job scraped out of a job-alert email. */
public record JobPosting(
        String id,
        String title,
        String company,
        String location,
        String applyUrl,
        String snippet,
        String jobDescription) {

    public JobPosting {
        id = id != null ? id : "";
        title = title != null ? title : "";
        company = company != null ? company : "";
        location = location != null ? location : "";
        applyUrl = applyUrl != null ? applyUrl : "";
        //    snippet = snippet != null ? snippet : "";
        jobDescription = jobDescription != null ? jobDescription : "";
    }

    public JobPosting withJobDescription(String jobDescription) {
        return new JobPosting(id, title, company, location, applyUrl, snippet, jobDescription);
    }

    /** The text the fitness tool matches against: everything we know about the posting. */
    public String searchableText() {
        return String.join(" ", title, company, location, snippet, jobDescription);
    }
}
