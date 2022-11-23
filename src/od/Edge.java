package od;

public class Edge {
    public Node end;
    public double weight;

    @Override
    public String toString() {
        return "(" + end + ")";
    }
}
