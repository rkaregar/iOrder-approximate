package od;
//import java.io.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.util.OpenBitSet;

public class Node {
    public String name;
    public long tID; // a tID representing the value of the equivalence class that the node came from
    public OpenBitSet attr;
    public List<Edge> connections;
    private Map<Long, Edge> neighbourID2edge;
    public int status;
    
    public Node() {
        connections = new ArrayList<>();
        neighbourID2edge = new HashMap<>();
        status = 1; // 1: Node stays in graph after singleton removal, 0: Node should get removed in the singleton removal process
    }
    
    public void addNeighbour(Node neighbour) {
        if (!neighbourID2edge.containsKey(neighbour.tID)) {
            Edge e = new Edge();
            e.end = neighbour;
            e.weight = 1;
            connections.add(e);
            neighbourID2edge.put(neighbour.tID, e);
        } else {
            neighbourID2edge.get(neighbour.tID).weight += 1;
        }
    }
    
    public ArrayList<Long> getConnectedtIDs() {
        ArrayList<Long> conntIDs = new ArrayList<>();
        for (int i = 0; i < connections.size(); i++) {
            conntIDs.add(connections.get(i).end.tID);
        }
        return conntIDs;
    }
    
    @Override
    public String toString() {
        StringBuilder res = new StringBuilder(name + ", " + tID + ": [");
        for (Edge e : connections) {
            res.append(e.end.tID).append("(").append((int)e.weight).append("), ");
        }
        return res + "]";
    }
}
