package com.example.rag.cli;

/** One-shot command modes, selected with {@code --rag.cli=index} or {@code --rag.cli=query}. */
public final class CliMode {

    public static final String PROPERTY = "rag.cli";
    public static final String INDEX = "index";
    public static final String QUERY = "query";

    private static final String ARGUMENT_PREFIX = "--" + PROPERTY + "=";

    private CliMode() {
    }

    public static boolean isRequested(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(ARGUMENT_PREFIX)) {
                String value = arg.substring(ARGUMENT_PREFIX.length()).trim();
                return !value.isEmpty() && !"none".equalsIgnoreCase(value);
            }
        }
        return false;
    }
}
