package od;

import org.sat4j.core.VecInt;
import org.sat4j.maxsat.SolverFactory;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import org.sat4j.pb.IPBSolver;

import java.math.BigInteger;
import java.util.*;

public class ApproxIISATReducer {
    private double threshold;
    private boolean onlyCheckUnconditional;
    
    private List<Graph> allBipGraphs;
    private List<List<Map<Long, Set<Long>>>> eachPGChains;
    
    private List<Graph> curBipGraphs;
    private int LHS_index, RHS_index;
    
    private int satVariableCounter;
    private List<Set<Long>> values;  // storing unique values in LHS and RHS
    public int maxCardinality;  // stores the maximum cardinality on each side for pruning purposes
    private List<Map<SATVariable, Integer>> satVariable2Numeric;  // encode LHS and RHS
    private Map<Integer, Integer> numericVar2side;  // this one will store the numeric variable's side,
    private Map<Integer, SATVariable> numericVar2satVar;  // while this one keeps its (a, b) equiv. variable
    public List<SimpleGraph> simpleUndirGraphs;  // holds the undir graphs of PGs to compute connected components
    
    public List<int[]> clauses;
    public List<Integer> weights; // -1 means infinity (hard clause)
    public Map<ClauseDoubleVar, Long> clause2weight;
    
    // We are wrapping the output in an ArrayList to be consistent with the conditional case
    // The first dimension is for each partition group (probably)
    public ArrayList<List<Map<Long, Set<Long>>>> finalOrder;
    public List<Double> approxScores;
    public List<List<List<Long>>> finalListOrder;
    public boolean isTheOrderConditional;
    
    
    public ApproxIISATReducer(List<Graph> allBipGraphs, List<List<Map<Long, Set<Long>>>> eachPGChain, int LHS_index,
                              int RHS_index, double threshold, boolean onlyCheckUnconditional) {
        this.threshold = threshold;
        this.onlyCheckUnconditional = onlyCheckUnconditional;
        this.allBipGraphs = allBipGraphs;
        this.eachPGChains = eachPGChain;
        
        this.LHS_index = LHS_index;
        this.RHS_index = RHS_index;
        
        isTheOrderConditional = true;
    }
    
    private void initializeVariables() {
        satVariable2Numeric = new ArrayList<>(Arrays.asList(new HashMap<>(), new HashMap<>()));
        numericVar2satVar = new HashMap<>();
        numericVar2side = new HashMap<>();
    
        clauses = new ArrayList<>();
        weights = new ArrayList<>();
        clause2weight = new HashMap<>();
        satVariableCounter = 1;
    
        finalOrder = new ArrayList<>();
        approxScores = new ArrayList<>();
        finalListOrder = new ArrayList<>();
    }
    
    public boolean reduceAndSolve(int initialCardinalityCutoff, int connectedComponentMaxSizeLimit) {
        long startTime = System.nanoTime();
    
        // For the unconditional case
        curBipGraphs = allBipGraphs;
        initializeVariables();
        
        findUniqueValues();
        
        if (Math.max(values.get(0).size(), values.get(1).size()) > MainClass.approxCardinalityThreshold) {
            return false;
        }
        
        if (true) {
            if (true) {
                startTime = System.nanoTime();
                createVariables();
                addSwapCluases();
                addTransitivityClauses();
                MainClass.reductionRuntime += (System.nanoTime() - startTime);

                boolean wasSuccessful = solveSAT();  // return if it was solved or exceeded time limit
                if (approxScores.get(0) >= threshold) {
                    isTheOrderConditional = false;
                    return true;
                }
            }
        }
        
        // For the conditional case
        if (!onlyCheckUnconditional) {
            for (Graph bipGraph : allBipGraphs) {
                curBipGraphs = new ArrayList<>();
                curBipGraphs.add(bipGraph);
        
                initializeVariables();
    
                startTime = System.nanoTime();
                findUniqueValues();
                createVariables();
                addSwapCluases();
                addTransitivityClauses();
                MainClass.reductionRuntime += (System.nanoTime() - startTime);
    
                solveSAT();
        
                if (approxScores.get(approxScores.size() - 1) < threshold) {
                    finalOrder = null;
                    return false;
                }
            }
            isTheOrderConditional = true;
        } else {
            finalOrder = null;
        }
        
        return true;
    }
    
    private void findUniqueValues() {
        values = new ArrayList<>();
        values.add(new HashSet<>());
        values.add(new HashSet<>());
        
        for (Graph bipGraph :
                curBipGraphs) {
            for (Node node :
                    bipGraph.nodes) {
                if (node.name.charAt(0) == 'l') {
                    values.get(0).add(node.tID);
                } else {
                    values.get(1).add(node.tID);
                }
            }
        }
        
        maxCardinality = Math.max(values.get(0).size(), values.get(1).size());
    }
    
    public void computeConnectedComponents() {
        simpleUndirGraphs = new ArrayList<>(Arrays.asList(new SimpleGraph(), new SimpleGraph()));
        // add the edges from the (potentially merged) PGs (chains)
        for (Graph graph : curBipGraphs) {
            for (Node node : graph.nodes) {
                int side = 1;
                if (node.name.charAt(0) == 'l') {
                    side = 0;
                }
                for (Edge edge : node.connections) {
                    simpleUndirGraphs.get(side).addEdge(node.tID, edge.end.tID);  // todo should be tID?
                }
            }
        }
        simpleUndirGraphs.get(0).computeConnectedComponents();
        simpleUndirGraphs.get(1).computeConnectedComponents();
    }
    
    private void createVariables() {
        for (int side = 0; side < 2; side++) {
        Set<Long> vals = values.get(side);
            List<Long> tmp = new ArrayList<>(vals);
            for (int i = 0; i < tmp.size() - 1; i++) {
                for (int j = i + 1; j < tmp.size(); j++) {
                    createTwoVariablesFromValues(side, tmp.get(i), tmp.get(j));
                }
            }
        }
    }
    
    private void createTwoVariablesFromValues(int side, long first, long second) {
        SATVariable satVar = new SATVariable(first, second), satVarRev = new SATVariable(second, first);
        if ((!satVariable2Numeric.get(side).containsKey(satVar)) && (!satVariable2Numeric.get(side).containsKey(satVarRev))) {
            satVariable2Numeric.get(side).put(satVar, satVariableCounter);
            numericVar2side.put(satVariableCounter, side);
            numericVar2satVar.put(satVariableCounter, satVar);
            
            satVariable2Numeric.get(side).put(satVarRev, satVariableCounter + 1);
            numericVar2side.put(satVariableCounter + 1, side);
            numericVar2satVar.put(satVariableCounter + 1, satVarRev);
            
            // add the 'not both true' clauses. not the cleanest design, but pretty efficient
            clauses.add(new int[]{-satVariableCounter, -(satVariableCounter + 1)});
            clauses.add(new int[]{satVariableCounter, satVariableCounter + 1});
            weights.add(-1);
            weights.add(-1);
            
            satVariableCounter += 2;
        }
    }
    
    public void addSwapCluases() {
        for (Graph bipGraph : curBipGraphs) {
            List<Node> lhsNodes = new ArrayList<>();  // only checking LHS nodes will cover all the necessary clauses
            for (Node node : bipGraph.nodes) {
                if (node.name.charAt(0) == 'l')
                    lhsNodes.add(node);
            }
            for (int i = 0; i < lhsNodes.size() - 1; i++) {
                Node firstNodeLhs = lhsNodes.get(i);
                for (Edge edge : firstNodeLhs.connections) {
                    Node firstNodeRhs = edge.end;
                    for (int j = i + 1; j < lhsNodes.size(); j++) {
                        Node secondNodeLhs = lhsNodes.get(j);
                        for (Edge edge2 : secondNodeLhs.connections) {
                            Node secondNodeRhs = edge2.end;
                            if (firstNodeRhs != secondNodeRhs) {  // add the swap clause
                                long weight = (long) (int) edge.weight * (int)edge2.weight;
                                
                                long l1 = firstNodeLhs.tID, r1 = firstNodeRhs.tID;
                                long l2 = secondNodeLhs.tID, r2 = secondNodeRhs.tID;
                            
                                int ac = satVariable2Numeric.get(0).get(new SATVariable(l1, l2));
                                int ca = satVariable2Numeric.get(0).get(new SATVariable(l2, l1));
                                int bd = satVariable2Numeric.get(1).get(new SATVariable(r1, r2));
                                int db = satVariable2Numeric.get(1).get(new SATVariable(r2, r1));
                            
                                ClauseDoubleVar clause1 = new ClauseDoubleVar(ac, db);
                                ClauseDoubleVar clause2 = new ClauseDoubleVar(ca, bd);
                                
                                if (!clause2weight.containsKey(clause1))
                                    clause2weight.put(clause1, 0L);
                                clause2weight.put(clause1, clause2weight.get(clause1) + weight);
                                if (!clause2weight.containsKey(clause2))
                                    clause2weight.put(clause2, 0L);
                                clause2weight.put(clause2, clause2weight.get(clause2) + weight);
                                
                            }
                        }
                    }
                }
            }
        }
    }
    
    public void addTransitivityClauses() {
        for (int side = 0; side < 2; side++) {
            List<Long> cc = new ArrayList<>(values.get(side));
            for (int i = 0; i < cc.size(); i++) {
                for (int j = 0; j < cc.size(); j++) {
                    for (int k = 0; k < cc.size(); k++) {
                        if ((i != j) && (i != k) && (j != k)) {
                            // these three are tIDs, or unique values in other words
                            Long v_i = cc.get(i);
                            Long v_j = cc.get(j);
                            Long v_k = cc.get(k);
                            
                            // add the clause for (ij && jk -> ik) === (!ij || !jk || ik)
                            int a = satVariable2Numeric.get(side).get(new SATVariable(v_i, v_j));
                            int b = satVariable2Numeric.get(side).get(new SATVariable(v_j, v_k));
                            int c = satVariable2Numeric.get(side).get(new SATVariable(v_i, v_k));
                            clauses.add(new int[]{-a, -b, c});
                            weights.add(-1);
                        }
                    }
                }
            }
        }
    }
    
    // We only need to check soft clauses
    public double computeScore(int[] assignments) {
        long totalScore = 0;
        long achievedScore = 0;
        
        for (ClauseDoubleVar clause : clause2weight.keySet()) {
            long weight = clause2weight.get(clause);
            totalScore += weight;
            int first = clause.first;
            int second = clause.second;
            
            if (assignments[first - 1] > 0 || assignments[second - 1] > 0) {
                achievedScore += weight;
            }
        }
    
        approxScores.add(totalScore == 0 ? 1. : (2. * (double) achievedScore / (double) totalScore) - 1.);
        return totalScore == 0 ? 1. : (2. * (double) achievedScore / (double) totalScore) - 1.;
    }
    
    // We are wrapping the output in an ArrayList to be consistent with the conditional case
    // Ok now we're not wrapping it here, the function that uses the result will do the wrapping lol
    // we just return a pair of orders for LHS/RHS in THIS partition group
    public List<Map<Long, Set<Long>>> getFinalOrders(int[] assignments) {
        // this is the only single chain for both sides, then we'll just append it to another list and return it
        List<Map<Long, Set<Long>>> doubleChain = new ArrayList<>(Arrays.asList(new HashMap<>(), new HashMap<>()));
        
        for (int side = 0; side < 2; side++) {
            for (Long val :
                    values.get(side)) {
                doubleChain.get(side).put(val, new HashSet<>());
            }
        }
        int side;
        SATVariable satVariable;
        
        for (int varAssignment :
                assignments) {
            if (varAssignment > 0) {
                side = numericVar2side.get(varAssignment);
                satVariable = numericVar2satVar.get(varAssignment);
                
                // if satVar = (a, b), we'll add the corresponding edge to the proper side graph
                
                long first = satVariable.getFirst();
                long second = satVariable.getSecond();
                doubleChain.get(side).get(first).add(second);
            }
        }
        
        List<List<Long>> curOrders = new ArrayList<>();
        curOrders.add(ODAlgorithm.totalOrder2List(doubleChain.get(0)));
        curOrders.add(ODAlgorithm.totalOrder2List(doubleChain.get(1)));
        finalListOrder.add(curOrders);
        
        return doubleChain;
    }
    
    public boolean solveSAT() {
        IPBSolver solver = SolverFactory.newDefault();
        WeightedMaxSatDecorator w = new WeightedMaxSatDecorator(solver);
        try {            
            w.newVar(satVariableCounter);
            for (int[] clause : clauses) {
                w.addHardClause(new VecInt(clause));
            }
            
            for (ClauseDoubleVar clause : clause2weight.keySet()) {
                w.addSoftClause(new BigInteger(String.valueOf(clause2weight.get(clause))), new VecInt(new int[]{clause.first, clause.second}));
            }
            
            long begin = System.nanoTime();
            w.isSatisfiable();
            MainClass.maxSatRuntime += (System.nanoTime() - begin);
            MainClass.maxSats++;

            finalOrder.add(getFinalOrders(w.model()));
            computeScore(w.model());
            
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        for (int[] clause :
                clauses) {
        }
        return false;
    }
}
