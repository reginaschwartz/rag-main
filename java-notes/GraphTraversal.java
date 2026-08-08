import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BFS explores in rings of increasing distance using a queue; DFS follows one branch to its end using
 * a stack (or the call stack). Both visit every reachable node once, in O(V + E), but the order and
 * the guarantees differ.
 */
class GraphTraversal {

    private final Map<String, List<String>> adjacency = new LinkedHashMap<>();

    void addEdge(String from, String to) {
        adjacency.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
        adjacency.computeIfAbsent(to, key -> new ArrayList<>()).add(from);
    }

    private List<String> neighbours(String node) {
        return adjacency.getOrDefault(node, List.of());
    }

    List<String> breadthFirst(String start) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        visited.add(start);     // mark when enqueued, not when dequeued, or nodes get queued twice
        queue.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            order.add(node);
            for (String neighbour : neighbours(node)) {
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return order;
    }

    List<String> depthFirst(String start) {
        List<String> order = new ArrayList<>();
        depthFirst(start, new HashSet<>(), order);
        return order;
    }

    private void depthFirst(String node, Set<String> visited, List<String> order) {
        if (!visited.add(node)) {
            return;             // the visited set is what stops cycles from looping forever
        }
        order.add(node);
        for (String neighbour : neighbours(node)) {
            depthFirst(neighbour, visited, order);
        }
    }

    /** The same walk without recursion: a queue becomes a stack, everything else is identical. */
    List<String> depthFirstIterative(String start) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();

        stack.push(start);
        while (!stack.isEmpty()) {
            String node = stack.pop();
            if (!visited.add(node)) {
                continue;
            }
            order.add(node);
            List<String> neighbours = neighbours(node);
            for (int i = neighbours.size() - 1; i >= 0; i--) {
                stack.push(neighbours.get(i));   // reversed so it matches the recursive order
            }
        }
        return order;
    }

    /** BFS reaches every node by the fewest edges, so the first time it sees the goal is the shortest path. */
    List<String> shortestPath(String start, String goal) {
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            if (node.equals(goal)) {
                return rebuildPath(parent, start, goal);
            }
            for (String neighbour : neighbours(node)) {
                if (visited.add(neighbour)) {
                    parent.put(neighbour, node);
                    queue.add(neighbour);
                }
            }
        }
        return List.of();
    }

    /** DFS finds a path, but the first one it stumbles into, which need not be short. */
    List<String> anyPath(String start, String goal) {
        List<String> path = new ArrayList<>();
        return anyPath(start, goal, new HashSet<>(), path) ? path : List.of();
    }

    private boolean anyPath(String node, String goal, Set<String> visited, List<String> path) {
        if (!visited.add(node)) {
            return false;
        }
        path.add(node);
        if (node.equals(goal)) {
            return true;
        }
        for (String neighbour : neighbours(node)) {
            if (anyPath(neighbour, goal, visited, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);   // dead end, undo this step
        return false;
    }

    private static List<String> rebuildPath(Map<String, String> parent, String start, String goal) {
        List<String> path = new ArrayList<>();
        for (String node = goal; node != null; node = parent.get(node)) {
            path.add(node);
            if (node.equals(start)) {
                break;
            }
        }
        return path.reversed();
    }

    public static void main(String[] args) {
        GraphTraversal graph = new GraphTraversal();
        // One short route A-C-G, one long detour A-B-D-E-F-G.
        graph.addEdge("A", "B");
        graph.addEdge("A", "C");
        graph.addEdge("B", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "G");
        graph.addEdge("C", "G");

        System.out.println("BFS from A      : " + graph.breadthFirst("A"));
        System.out.println("DFS from A      : " + graph.depthFirst("A"));
        System.out.println("DFS iterative   : " + graph.depthFirstIterative("A"));

        List<String> shortest = graph.shortestPath("A", "G");
        List<String> any = graph.anyPath("A", "G");
        System.out.println("BFS path A -> G : " + shortest + "  (" + (shortest.size() - 1) + " edges)");
        System.out.println("DFS path A -> G : " + any + "  (" + (any.size() - 1) + " edges)");
    }
}
