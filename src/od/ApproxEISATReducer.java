package od;

import org.sat4j.core.VecInt;
import org.sat4j.maxsat.SolverFactory;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import org.sat4j.pb.IPBSolver;

import java.math.BigInteger;
import java.util.*;

public class ApproxEISATReducer {
    private double threshold;
    private boolean onlyCheckUnconditional;
    
    int globalM;  // Denotes the number of unique values
    List<Integer> allMPerPG;
    long[][] globalAdjMatrix;
    List<long[][]> allAdjs;
    private List<Long> globalUniqValToTupID;
    private List<List<Long>> perPGUniqValToTupID;
    private int RHS_index;
    
    int curM;  // Denotes the number of unique values for the current computation
    long[][] curAdjMatrix;
    private List<Long> curUniqValToTupID;
    
    private int satVariableCounter;
    private Map<SATVariable, Integer> satVariable2Numeric;  // encode LHS and RHS
    private Map<Integer, SATVariable> numericVar2satVar;  // while this one keeps its (a, b) equiv. variable
    
    public List<int[]> clauses;
    public List<Long> weights; // -1 means infinity (hard clause)
    
    public List<Map<Long, Set<Long>>> finalOrder;
    public List<List<Long>> finalListOrder;
    public List<Double> approxScores;
    
    public boolean isTheOrderConditional;
    
    public ApproxEISATReducer(long[][] adjMatrix, int m, List<long[][]> allAdjs, List<Long> uniqValToTupID,
                              List<List<Long>> perPGUniqValToTupID, int RHS_index, double threshold, boolean onlyCheckUnconditional) {
        this.threshold = threshold;
        this.onlyCheckUnconditional = onlyCheckUnconditional;
        
        this.globalM = m;
        this.globalAdjMatrix = adjMatrix;
        this.allAdjs = allAdjs;
        this.globalUniqValToTupID = uniqValToTupID;
        this.perPGUniqValToTupID = perPGUniqValToTupID;
        this.RHS_index = RHS_index;
        
        allMPerPG = new ArrayList<>();
        for (long[][] it : allAdjs) {
            allMPerPG.add(it.length);
        }
        
        isTheOrderConditional = true;
    }
    
    private void initializeVars() {
        satVariableCounter = 1;
        satVariable2Numeric = new HashMap<>();
        numericVar2satVar = new HashMap<>();
    
        clauses = new ArrayList<>();
        weights = new ArrayList<>();
        
        finalOrder = new ArrayList<>();
        finalListOrder = new ArrayList<>();
        approxScores = new ArrayList<>();
    }
    
    public boolean reduceAndSolve(int initialCardinalityCutoff, int connectedComponentMaxSizeLimit) {
        long startTime;
        
        if (globalM > MainClass.approxCardinalityThreshold) {
            return false;
        }
        
        // For the unconditional case
        curAdjMatrix = globalAdjMatrix;
        curM = globalM;
        curUniqValToTupID = globalUniqValToTupID;
    
        initializeVars();
    
        startTime = System.nanoTime();
        createVariables();
        addSwapCluases();
        addTransitivityClauses();
        MainClass.reductionRuntime += (System.nanoTime() - startTime);
        
        solveSAT();
        if (approxScores.get(0) >= threshold) {
            isTheOrderConditional = false;
            return true;
        }
        
        // For the conditional case
        if (!onlyCheckUnconditional) {
            for (int i = 0; i < allAdjs.size(); i++) {
                curAdjMatrix = allAdjs.get(i);
                curM = allMPerPG.get(i);
                curUniqValToTupID = perPGUniqValToTupID.get(i);
        
                initializeVars();
    
                startTime = System.nanoTime();
                createVariables();
                addSwapCluases();
                addTransitivityClauses();
                solveSAT();
                MainClass.reductionRuntime += (System.nanoTime() - startTime);
    
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
    
    private void createVariables() {
        for (int i = 0; i < curM - 1; i++) {
            for (int j = i + 1; j < curM; j++) {
                createTwoVariablesFromValues(i, j);
            }
        }
    }
    
    private void createTwoVariablesFromValues(long first, long second) {
        SATVariable satVar = new SATVariable(first, second), satVarRev = new SATVariable(second, first);
        if ((!satVariable2Numeric.containsKey(satVar)) && (!satVariable2Numeric.containsKey(satVarRev))) {
            satVariable2Numeric.put(satVar, satVariableCounter);
            numericVar2satVar.put(satVariableCounter, satVar);
            
            satVariable2Numeric.put(satVarRev, satVariableCounter + 1);
            numericVar2satVar.put(satVariableCounter + 1, satVarRev);
            
            // add the 'not both true' clauses. not the cleanest design, but pretty efficient
            clauses.add(new int[]{-satVariableCounter, -(satVariableCounter + 1)});
            clauses.add(new int[]{satVariableCounter, satVariableCounter + 1});
            weights.add(-1L);
            weights.add(-1L);
            
            satVariableCounter += 2;
        }
    }
    
    public void addSwapCluases() {
        for (int i = 0; i < curM - 1; i++) {
            for (int j = i + 1; j < curM; j++) {
                if (curAdjMatrix[i][j] > curAdjMatrix[j][i]) {
                    clauses.add(new int[]{satVariable2Numeric.get(new SATVariable(i, j))});
                    weights.add(curAdjMatrix[i][j] - curAdjMatrix[j][i]);
                } else if (curAdjMatrix[j][i] > curAdjMatrix[i][j]) {
                    clauses.add(new int[]{satVariable2Numeric.get(new SATVariable(j, i))});
                    weights.add(curAdjMatrix[j][i] - curAdjMatrix[i][j]);
                }
            }
        }
        
    }
    
    public void addTransitivityClauses() {
        for (int i = 0; i < curM; i++) {
            for (int j = 0; j < curM; j++) {
                for (int k = 0; k < curM; k++) {
                    if ((i != j) && (i != k) && (j != k)) {
                        // add the clause for (ij && jk -> ik) === (!ij || !jk || ik)
                        int a = satVariable2Numeric.get(new SATVariable(i, j));
                        int b = satVariable2Numeric.get(new SATVariable(j, k));
                        int c = satVariable2Numeric.get(new SATVariable(i, k));
                        clauses.add(new int[]{-a, -b, c});
                        weights.add(-1L);
                    }
                }
            }
        }
    }
    
    // We only need to check soft clauses
    public double computeScore(int[] assignments) {
        long totalScore = 0;
        long achievedScore = 0;
    
        for (int i = 0; i < curM; i++) {
            for (int j = 0; j < curM; j++) {
                if (i != j) {
                    totalScore += curAdjMatrix[i][j];
                    int satVarNumber = satVariable2Numeric.get(new SATVariable(i, j));
                    if (assignments[satVarNumber - 1] > 0) {
                        achievedScore += curAdjMatrix[i][j];
                    }
                }
            }
        }
        
        approxScores.add(totalScore == 0 ? 1. : (double) achievedScore / (double) totalScore);
        return totalScore == 0 ? 1. : (double) achievedScore / (double) totalScore;
    }
    
    // We are wrapping the output in a List to be consistent with the conditional case
    public Map<Long, Set<Long>> getFinalOrders(int[] assignments) {
        // this is the only single chain for both sides, then we'll just append it to another list and return it
        Map<Long, Set<Long>> chain = new HashMap<>();
    
        for (long i = 0; i < curM; i++) {
            chain.put(i, new HashSet<>());
        }
        
        SATVariable satVariable;
        
        for (int varAssignment :
                assignments) {
            if (varAssignment > 0) {
                satVariable = numericVar2satVar.get(varAssignment);
                
                // if satVar = (a, b), we'll add the corresponding edge to the graph
                long first = satVariable.getFirst();
                long second = satVariable.getSecond();
                chain.get(first).add(second);
            }
        }
        
        List<Long> curListOrder = ODAlgorithm.totalOrder2List(chain);
        for (int i = 0; i < curListOrder.size(); i++) {
            curListOrder.set(i, curUniqValToTupID.get(curListOrder.get(i).intValue()));
        }
        finalListOrder.add(curListOrder);
        return chain;
    }
    
    public boolean solveSAT() {
        IPBSolver solver = SolverFactory.newDefault();
        WeightedMaxSatDecorator w = new WeightedMaxSatDecorator(solver);
        try {
            int cnt = 0;
            for (int i = 0; i < clauses.size(); i++)
                if (weights.get(i) > 0)
                    cnt++;
            
            w.newVar(satVariableCounter);
            for (int i = 0; i < clauses.size(); i++) {
                if (weights.get(i) < 0) {
                    w.addHardClause(new VecInt(clauses.get(i)));
                } else {
                    w.addSoftClause(new BigInteger(String.valueOf(weights.get(i))), new VecInt(clauses.get(i)));
                }
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
        return false;
    }
    
}
