import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Corrective RAG as a state graph: retrieve, grade what came back, and if it is useless rewrite the
 * question and retrieve again. The cycle is the point - a linear chain cannot go backwards.
 *
 * The retriever and the two "LLM" steps are deterministic stubs so the graph mechanics are visible
 * without an API key.
 */
public class CorrectiveRagGraph {

    private static final int MAX_ATTEMPTS = 2;

    /** Serializable because the graph deep-clones state between nodes to keep each step immutable. */
    record Doc(String id, String text) implements Serializable {
    }

    static class RagState extends AgentState {
        static final String QUESTION = "question";
        static final String DOCUMENTS = "documents";
        static final String RELEVANT = "relevant";
        static final String ATTEMPTS = "attempts";
        static final String ANSWER = "answer";
        static final String TRACE = "trace";

        /** Only TRACE declares a reducer; every other key uses the default last-write-wins. */
        static final Map<String, Channel<?>> SCHEMA = Map.of(
                TRACE, Channels.appender(ArrayList::new)
        );

        RagState(Map<String, Object> initData) {
            super(initData);
        }

        String question() {
            return this.<String>value(QUESTION).orElse("");
        }

        List<Doc> documents() {
            return this.<List<Doc>>value(DOCUMENTS).orElse(List.of());
        }

        boolean relevant() {
            return this.<Boolean>value(RELEVANT).orElse(false);
        }

        int attempts() {
            return this.<Integer>value(ATTEMPTS).orElse(0);
        }

        String answer() {
            return this.<String>value(ANSWER).orElse("");
        }

        List<String> trace() {
            return this.<List<String>>value(TRACE).orElse(List.of());
        }
    }

    // --- nodes: each takes the state and returns only the keys it wants to change ---

    static Map<String, Object> retrieve(RagState state) {
        List<Doc> hits = search(state.question());
        return Map.of(
                RagState.DOCUMENTS, hits,
                RagState.TRACE, "retrieve   | \"%s\" -> %d hit(s) %s"
                        .formatted(state.question(), hits.size(), hits.stream().map(Doc::id).toList()));
    }

    /** Stands in for an LLM grader scoring retrieved context against the question. */
    static Map<String, Object> grade(RagState state) {
        boolean relevant = !state.documents().isEmpty();
        int attempts = state.attempts() + 1;
        return Map.of(
                RagState.RELEVANT, relevant,
                RagState.ATTEMPTS, attempts,
                RagState.TRACE, "grade      | attempt %d -> %s".formatted(attempts, relevant ? "RELEVANT" : "IRRELEVANT"));
    }

    /** Stands in for an LLM rewriting a vague question into retrievable domain terms. */
    static Map<String, Object> rewrite(RagState state) {
        String improved = expand(state.question());
        return Map.of(
                RagState.QUESTION, improved,
                RagState.TRACE, "rewrite    | \"%s\" -> \"%s\"".formatted(state.question(), improved));
    }

    /** Stands in for the answer-generation LLM call, grounded in whatever survived grading. */
    static Map<String, Object> generate(RagState state) {
        Doc top = state.documents().getFirst();
        String answer = "%s  (source: %s)".formatted(top.text(), top.id());
        return Map.of(
                RagState.ANSWER, answer,
                RagState.TRACE, "generate   | grounded in " + top.id());
    }

    static Map<String, Object> giveUp(RagState state) {
        return Map.of(
                RagState.ANSWER, "Unable to find relevant context after %d attempts.".formatted(state.attempts()),
                RagState.TRACE, "give-up    | retry budget exhausted");
    }

    /** The conditional edge: reads state, returns the name of the branch to follow. */
    static String route(RagState state) {
        if (state.relevant()) {
            return "relevant";
        }
        return state.attempts() < MAX_ATTEMPTS ? "retry" : "exhausted";
    }

    public static void main(String[] args) throws Exception {
        var workflow = new StateGraph<>(RagState.SCHEMA, RagState::new)
                .addNode("retrieve", node_async(CorrectiveRagGraph::retrieve))
                .addNode("grade", node_async(CorrectiveRagGraph::grade))
                .addNode("rewrite", node_async(CorrectiveRagGraph::rewrite))
                .addNode("generate", node_async(CorrectiveRagGraph::generate))
                .addNode("give_up", node_async(CorrectiveRagGraph::giveUp))
                .addEdge(START, "retrieve")
                .addEdge("retrieve", "grade")
                .addConditionalEdges("grade", edge_async(CorrectiveRagGraph::route), Map.of(
                        "relevant", "generate",
                        "retry", "rewrite",
                        "exhausted", "give_up"))
                .addEdge("rewrite", "retrieve")     // the cycle
                .addEdge("generate", END)
                .addEdge("give_up", END);

        var app = workflow.compile();

        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Corrective RAG").content());

        String question = "how do I make the thing faster?";
        System.out.println("question: " + question + "\n");

        var finalState = app.invoke(Map.of(RagState.QUESTION, question)).orElseThrow();

        finalState.trace().forEach(step -> System.out.println("  " + step));
        System.out.println("\nanswer: " + finalState.answer());
    }

    // --- stubs standing in for a vector store and two LLM calls ---

    private static final List<Doc> CORPUS = List.of(
            new Doc("chunking.md",
                    "Documents are split by a recursive character text splitter with chunk size 300 and overlap 100."),
            new Doc("pgvector-index.md",
                    "Query latency drops sharply once an HNSW index exists on the embedding column, "
                            + "because without an index every query falls back to a sequential scan."),
            new Doc("embeddings.md",
                    "The ada-002 model returns 1536 dimensional vectors and requests are batched to cut round trips."),
            new Doc("deployment.md",
                    "The service runs under Docker alongside Postgres, and init.sql installs the vector extension."));

    private static final Set<String> STOPWORDS =
            Set.of("how", "do", "i", "the", "a", "an", "make", "thing", "to", "my", "is", "it", "get");

    private static List<Doc> search(String question) {
        List<String> terms = Arrays.stream(question.toLowerCase().split("\\W+"))
                .filter(term -> term.length() > 2 && !STOPWORDS.contains(term))
                .toList();

        Map<Doc, Long> scored = new LinkedHashMap<>();
        for (Doc doc : CORPUS) {
            String text = doc.text().toLowerCase();
            long score = terms.stream().filter(text::contains).count();
            if (score >= 2) {
                scored.put(doc, score);
            }
        }
        return scored.entrySet().stream()
                .sorted(Map.Entry.<Doc, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private static final Map<String, String> SYNONYMS = Map.of(
            "faster", "query latency index",
            "slow", "query latency index",
            "big", "chunk size overlap");

    private static String expand(String question) {
        StringBuilder rewritten = new StringBuilder();
        for (String word : question.toLowerCase().split("\\W+")) {
            String replacement = SYNONYMS.get(word);
            if (replacement != null) {
                rewritten.append(replacement).append(' ');
            }
        }
        return rewritten.isEmpty() ? question : rewritten.toString().trim();
    }
}
