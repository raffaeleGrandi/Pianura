package jusePack.comm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Who can reach whom at a given simulated time. Immutable.
 *
 * The graph is directed: {@code reaches(i, j)} is true when robot i transmits far enough to
 * be heard by robot j. With equal ranges every link works both ways.
 * Nodes are indexed 0..size()-1 in scenario order; {@link #id(int)} gives the robot id.
 */
public final class ConnectivityGraph {

    /** A link between two robots (indices a &lt; b) and the directions in which it works. */
    public record Link(int a, int b, boolean aToB, boolean bToA) {
        public boolean bidirectional() { return aToB && bToA; }
    }

    private final double time;
    private final int[] ids;
    private final boolean[][] reach;
    private final List<Link> links;
    private final int[] component;
    private final int componentCount;

    public ConnectivityGraph(double time, int[] ids, boolean[][] reach) {
        this.time = time;
        this.ids = ids.clone();
        int n = ids.length;
        this.reach = new boolean[n][];
        for (int i = 0; i < n; i++) this.reach[i] = reach[i].clone();

        List<Link> l = new ArrayList<>();
        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++)
                if (this.reach[a][b] || this.reach[b][a]) l.add(new Link(a, b, this.reach[a][b], this.reach[b][a]));
        this.links = List.copyOf(l);

        // componenti debolmente connesse (union-find)
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (Link k : links) union(parent, k.a(), k.b());
        component = new int[n];
        int[] label = new int[n];
        Arrays.fill(label, -1);
        int c = 0;
        for (int i = 0; i < n; i++) {
            int r = find(parent, i);
            if (label[r] < 0) label[r] = c++;
            component[i] = label[r];
        }
        componentCount = c;
    }

    private static int find(int[] p, int i) { while (p[i] != i) { p[i] = p[p[i]]; i = p[i]; } return i; }

    private static void union(int[] p, int a, int b) { p[find(p, a)] = find(p, b); }

    public static ConnectivityGraph empty() { return new ConnectivityGraph(0, new int[0], new boolean[0][0]); }

    public double getTime() { return time; }

    public int size() { return ids.length; }

    /** Robot id of node {@code index}. */
    public int id(int index) { return ids[index]; }

    /** Node index of robot {@code id}, or -1. */
    public int indexOf(int id) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        return -1;
    }

    /** True if robot {@code i} is heard by robot {@code j} (indices). */
    public boolean reaches(int i, int j) { return reach[i][j]; }

    /** Links (each pair once), in order. */
    public List<Link> links() { return links; }

    /** Directed adjacency matrix: a[i][j] = 1 if i reaches j. */
    public int[][] adjacency() {
        int n = ids.length;
        int[][] a = new int[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) a[i][j] = reach[i][j] ? 1 : 0;
        return a;
    }

    /**
     * Incidence matrix, robots × links (columns in the order of {@link #links()}):
     * a two-way link has 1 on both robots; a one-way link has +1 on the transmitter
     * and −1 on the receiver.
     */
    public int[][] incidence() {
        int n = ids.length;
        int[][] m = new int[n][links.size()];
        for (int k = 0; k < links.size(); k++) {
            Link l = links.get(k);
            if (l.bidirectional()) { m[l.a()][k] = 1; m[l.b()][k] = 1; }
            else if (l.aToB()) { m[l.a()][k] = 1; m[l.b()][k] = -1; }
            else { m[l.b()][k] = 1; m[l.a()][k] = -1; }
        }
        return m;
    }

    /** Weakly connected component of each node (0..componentCount()-1). */
    public int component(int index) { return component[index]; }

    public int componentCount() { return componentCount; }

    /** Number of robots in component {@code c}. */
    public int componentSize(int c) {
        int n = 0;
        for (int x : component) if (x == c) n++;
        return n;
    }

    /** True if node {@code index} has no link at all. */
    public boolean isolated(int index) { return componentSize(component[index]) == 1; }

    /** Number of robots that hear robot {@code index}. */
    public int outDegree(int index) {
        int d = 0;
        for (boolean b : reach[index]) if (b) d++;
        return d;
    }

}
