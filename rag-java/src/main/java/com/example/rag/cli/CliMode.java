package com.example.rag.cli;

/** One-shot command modes, selected with {@code --rag.cli=index|query|jobs}. */
public final class CliMode {

    public static final String PROPERTY = "rag.cli";
    public static final String INDEX = "index";
    public static final String QUERY = "query";
    public static final String JOBS = "jobs";

    private static final String ARGUMENT_PREFIX = "--" + PROPERTY + "=";

    private CliMode() {
    }

    public static boolean isRequested(String[] args) {
        String value = valueOf(args);
        return value != null && !value.isEmpty() && !"none".equalsIgnoreCase(value);
    }

    public static boolean isJobs(String[] args) {
        return JOBS.equalsIgnoreCase(valueOf(args));
    }

    private static String valueOf(String[] args) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(ARGUMENT_PREFIX)) {
                return arg.substring(ARGUMENT_PREFIX.length()).trim();
            }
        }
        return null;
    }
}
