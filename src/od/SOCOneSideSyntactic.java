package od;

import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;

public class SOCOneSideSyntactic {
    private List<Map<Long, Long>> TAU_reverseMap;
    private int B_index;
    private int A_index;
    private boolean fdHolds;
    public List<List<Map<Long, Set<Long>>>> allChainsNoFD;
    public List<Map<Long, Set<Long>>> allChains;
    private ObjectBigArrayBigList<ObjectBigArrayBigList<LongBigArrayBigList>> TAU_XA;
    private ObjectBigArrayBigList<ObjectBigArrayBigList<LongBigArrayBigList>> TAU_XB;
    
    public boolean holds;
    public boolean isOrderConditional = true;
    public boolean isOrderApproximate = false;
    public List<Double> approxScores = null;
    
    private List<Long> uniqValToTupID4Approx;  // this stores the mapping for the approx. results, to get the tuple values
    private List<List<Long>> eachPGUniqValToTupID4Approx;  // storing the same info within each PG
    
    public SOCOneSideSyntactic(List<Map<Long, Long>> TAU_reverseMap, int B_index, int A_index, boolean fdHolds,
                               ObjectBigArrayBigList<ObjectBigArrayBigList<LongBigArrayBigList>> TAU_XA,
                               ObjectBigArrayBigList<ObjectBigArrayBigList<LongBigArrayBigList>> TAU_XB) {
        this.TAU_reverseMap = TAU_reverseMap;
        this.TAU_XA = TAU_XA;
        this.TAU_XB = TAU_XB;
        this.A_index = A_index;
        this.B_index = B_index;
        this.fdHolds = fdHolds;
        allChains = new ArrayList<>();
        allChainsNoFD = new ArrayList<>();
    }
    
    public List<Map<Long, Set<Long>>> executeApprox(boolean onlyUnconditional) {
        System.out.println("EI is null, let's goooo");
    
        List<List<List<Integer>>> occur4approxWithinEachPG = getOccur4ApproxInEachPG();
        List<long[][]> approxAdjWithinEachPG = getApproxAdjMatrixForEachPG(occur4approxWithinEachPG);
    
        List<List<List<Integer>>> occur4approx = getOccur4Approx();
        long[][] approxAdj = getApproxAdjMatrix(occur4approx, occur4approx.get(0).get(0).size());
    
        ApproxEISATReducer approxEISATReducer = new ApproxEISATReducer(approxAdj, approxAdj.length,
                approxAdjWithinEachPG, uniqValToTupID4Approx, eachPGUniqValToTupID4Approx, B_index, MainClass.ApproxThreshold, onlyUnconditional);
        approxEISATReducer.reduceAndSolve(1000, 1000);
    
        holds = (approxEISATReducer.finalOrder != null);
        isOrderApproximate = true;
        isOrderConditional = approxEISATReducer.isTheOrderConditional;
        approxScores = approxEISATReducer.approxScores;
        return approxEISATReducer.finalOrder;
    }
    
    public List<Map<Long, Set<Long>>> executeExact() {
        // there are |X| entries in TAU_XA
        // ith entry in TAU_XA contains entries for ith equivalence class of X, but partitioned with values of A
        
        List<Map<Long, Set<Long>>> exactRes;
        if (fdHolds) {
            exactRes = executeWithFD();
        } else {
            exactRes = executeNoFD();
        }
        holds = (exactRes != null);
        return exactRes;
    }
    
    private List<Map<Long, Set<Long>>> executeNoFD() {
        boolean valid = deriveChainsNoFD();
        if (!valid) {
            return null;
        }
        if (allChainsNoFD.size() == 1) {  // it's also unconditional
            isOrderConditional = false;
            return new ArrayList<>(Collections.singletonList(allChainsNoFD.get(0).get(1)));
        }
        //merge everything in the chains (only RHS)
        Map<Long, Set<Long>> mergedChains = new HashMap<>();
        for (List<Map<Long, Set<Long>>> chains : allChainsNoFD) {
            Map<Long, Set<Long>> currChain = chains.get(1);
            allChains.add(currChain);
            mergeChains(mergedChains, currChain);
        }

        if ((new Graph2(mergedChains)).isCyclic()) {  // this is counted as conditional now
            return allChains;
        }
        return new ArrayList<>(Collections.singletonList(mergedChains));
    }
    
    private void mergeChains(Map<Long, Set<Long>> adj1, Map<Long, Set<Long>> adj2) {
        for (long key : adj2.keySet()) {
            if (!adj1.containsKey(key)) {
                adj1.put(key, new HashSet<>());
            }
            adj1.get(key).addAll(adj2.get(key));
        }
    }
    
    
    private List<Map<Long, Set<Long>>> executeWithFD() {
        for (ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS : TAU_XA) {// iterate over each eq. class in X
            ArrayList<Long> listRHS = getListRHSwithFD(sorted_TAU_LHS);
            if (listRHS == null) { // at least one of the chains is individually invalid, this is not even conditional
                return null;
            }
            addChain(deriveChainWithFD(listRHS));
        }
        Map<Long, Set<Long>> adjList = mergeChains(allChains);
        boolean isValid = validateCandidate(adjList);
        if (isValid) {  // it's legit unconditional
            isOrderConditional = false;
            return new ArrayList<>(Collections.singletonList(adjList));
        } else {  // it's conditional
            return allChains;
        }
    }
    
    private Map<Long, Set<Long>> getSingleChain() {
        Map<Long, Set<Long>> chain = new HashMap<>();
        // TAU_XA , TAU_reverseMap, B_index
        for (ObjectBigArrayBigList<LongBigArrayBigList> eqC_X : TAU_XA) {
            // one chain should come out of this for loop after we're done with the current X equivalence class
            for (LongBigArrayBigList eqC_XA : eqC_X) {
                Set<Long> BtIDs = new HashSet<>();
                for (long AtID : eqC_XA) {
                    BtIDs.add(TAU_reverseMap.get(B_index - 1).get(AtID));
                }
                
                // map all tIDs in eqC_XA to their corresponding tID representatives of B's equivalence classes
                // then get union with the prev eqC_XA
            }
        }
        return chain;
    }
    
    private ArrayList<Long> getListRHSwithFD(ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS) {
        ArrayList<Long> listRHS = new ArrayList<>();
        Set<Long> setRHS = new HashSet<>();
        for (LongBigArrayBigList XA_eqC : sorted_TAU_LHS) {
            long tIDRHS = TAU_reverseMap.get(B_index - 1).get(XA_eqC.get(0)); // this only works if an FD holds,
            // if an FD doesn't hold, then we need to iterate over all tIDs in XA_eqC, not just index 0 of it.
            
            if (setRHS.contains(tIDRHS)) {
                if (listRHS.get(listRHS.size() - 1) != tIDRHS) {
                    return null;
                }
            } else {
                listRHS.add(tIDRHS);
                setRHS.add(tIDRHS);
            }
        }
        return listRHS;
    }
    
    
    private Map<Long, Set<Long>> deriveChainWithFD(List<Long> listRHS) {
        Map<Long, Set<Long>> chain = new HashMap<>();
        for (int i = 0; i < (listRHS.size() - 1); i++) {
            chain.put(listRHS.get(i), new HashSet<>(Collections.singletonList(listRHS.get(i + 1))));
        }
        chain.put(listRHS.get(listRHS.size() - 1), new HashSet<>());
        return chain;
    }
    
    private boolean deriveChainsNoFD() { //derives chains and returns true if all individual chains were acyclic, and false otherwise
        allChainsNoFD = new ArrayList<>();
        for (int i = 0; i < TAU_XA.size64(); i++) { // or TAU_XB -- iterating over eq. classes on X
            ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS = TAU_XA.get(i); //TAU_SortedList.get(A.nextSetBit(0)-1);// start could be anywhere for now because we are restricted to L = 2 for now
            ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_RHS = TAU_XB.get(i);//TAU_SortedList.get(B.nextSetBit(0)-1); // compare value in parantheses to A's value
            
            Map<Long, Integer> rhsMap = new HashMap<>(); // will include the mapping from Tuple ID (tID) to the eq class within sorted_TAU_RHS that it falls under
            int loopC = 0; // counter keeping the index for the eq class we're on
            
            for (LongBigArrayBigList eqC_rhs : sorted_TAU_RHS) { // building the reverse mapping (from tID to equivalence class index)
                for (long tID : eqC_rhs) {
                    rhsMap.put(tID, loopC);
                }
                // create the status array for bipartite graph
                loopC++;
            }
            
            Map<String, Node> nodesMap = new HashMap<>();
            long tID = -1;
            loopC = 0;
            int eqC_rhs_idx = -1;
            LinkedHashSet<Long> sortedLHSeqCs = new LinkedHashSet<>();
            for (LongBigArrayBigList eqC_lhs : sorted_TAU_LHS) { // now within the eq. class on X, iterating over eq. classes on LHS
                // add
                Node curr_lhs_node = new Node();
                
                tID = eqC_lhs.get(0); // Added Apr 30
                sortedLHSeqCs.add((long) TAU_reverseMap.get(A_index - 1).get(tID));
                
                curr_lhs_node.name = String.format("l-%d", loopC);
                curr_lhs_node.tID = (long) TAU_reverseMap.get(A_index - 1).get(tID);
                nodesMap.put(curr_lhs_node.name, curr_lhs_node);
                
                int j = 0;
                while (j < eqC_lhs.size64()) {
                    tID = eqC_lhs.getLong(j);
                        try {
                            eqC_rhs_idx = rhsMap.get(tID);
                            LongBigArrayBigList eqC_rhs = sorted_TAU_RHS.get(eqC_rhs_idx);  // id's of tuples in this eq.
                            String rhsNodeKey = String.format("r-%d", eqC_rhs_idx);
                            
                            Node curr_rhs_node = nodesMap.get(rhsNodeKey);
                            if (curr_rhs_node == null) {
                                curr_rhs_node = new Node();
                                curr_rhs_node.name = rhsNodeKey;
                                curr_rhs_node.tID = (long) TAU_reverseMap.get(B_index - 1).get(tID);
                                nodesMap.put(curr_rhs_node.name, curr_rhs_node);
                            }

                            curr_lhs_node.addNeighbour(curr_rhs_node);
                            curr_rhs_node.addNeighbour(curr_lhs_node);

                        } catch (Exception e) {
                            eqC_rhs_idx = -1;
                        }
                    j++;
                }
                loopC++;
            }
            
            Graph bipGraph = new Graph();
            bipGraph.nodes = new ArrayList<>(nodesMap.values());
            
            for (Node node : bipGraph.nodes) {
                if (node.connections.size() == 1) {
                    node.status = 0;
                }// node should be tagged as "to be removed"
            }
            bipGraph.initialize();
            if (bipGraph.isCyclic()) {
                return false;
            }
            ArrayList<Map<Long, Set<Long>>> chains = bipGraph.getChainAdjsSpecSOC(new ArrayList<>(sortedLHSeqCs));
            if (chains == null) {
                return false;
            }
            allChainsNoFD.add(chains);
        }
        return true;
    }
    
    
    private boolean validateCandidate(Map<Long, Set<Long>> adjList) {
        Graph2 graph = new Graph2(adjList);
        return (!graph.isCyclic());
    }
    
    private void addChain(Map<Long, Set<Long>> newChain) {
        allChains.add(newChain);
    }
    
    private List<List<List<Integer>>> getOccur4Approx() {
        List<List<List<Integer>>> res = new ArrayList<>();
        Map<Long, Integer> bEq2Counter = new HashMap<>();
        int bCnt = 0;
        long eqOfB;
    
        uniqValToTupID4Approx = new ArrayList<>();
        
        for (ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS : TAU_XA) { // iterating over eq. classes on X
            for (LongBigArrayBigList eqC_lhs : sorted_TAU_LHS) {
                for (long tID : eqC_lhs) {
                    eqOfB = TAU_reverseMap.get(B_index - 1).get(tID);
                    if (!bEq2Counter.containsKey(eqOfB)) {
                        bEq2Counter.put(eqOfB, bCnt);
                        uniqValToTupID4Approx.add(tID);
                        bCnt++;
                    }
                }
            }
        }
        
        int curBCnt;
        
        for (ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS : TAU_XA) {
        List<List<Integer>> curECXRes = new ArrayList<>();
            for (LongBigArrayBigList eqC_lhs : sorted_TAU_LHS) {
                List<Integer> curECACounts = new ArrayList<>(Collections.nCopies(bCnt, 0));
                for (long tID : eqC_lhs) {
                    curBCnt = bEq2Counter.get(TAU_reverseMap.get(B_index - 1).get(tID));
                    curECACounts.set(curBCnt, curECACounts.get(curBCnt) + 1);
                }
                curECXRes.add(curECACounts);
            }
            res.add(curECXRes);
        }
    
        
        return res;
    }
    
    private long[][] getApproxAdjMatrix (List<List<List<Integer>>> occurrances, int bEqCnt) {
        long[][] res = new long[bEqCnt][bEqCnt];
        int[] curVec;
        for (List<List<Integer>> oneEq : occurrances) {
            curVec = new int[bEqCnt];
            for (List<Integer> counts : oneEq) {
                for (int i = 0; i < counts.size(); i++) {
                    if (counts.get(i) > 0) {
                        for (int j = 0; j < curVec.length; j++) {
                            res[j][i] += (long) curVec[j] * counts.get(i);
                        }
                    }
                }
                for (int i = 0; i < counts.size(); i++) {
                    curVec[i] += counts.get(i);
                }
            }
        }
        return res;
    }
    
    private List<List<List<Integer>>> getOccur4ApproxInEachPG() {
        List<List<List<Integer>>> res = new ArrayList<>();
        
        eachPGUniqValToTupID4Approx = new ArrayList<>();
        List<Map<Long, Integer>> allbEq2Counter = new ArrayList<>();
        List<Integer> allBcnts = new ArrayList<>();
        
        for (ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS : TAU_XA) { // iterating over eq. classes on X
            List<Long> thisPGUniques = new ArrayList<>();
            Map<Long, Integer> bEq2Counter = new HashMap<>();
            int bCnt = 0;
            long eqOfB;
            
            for (LongBigArrayBigList eqC_lhs : sorted_TAU_LHS) {
                for (long tID : eqC_lhs) {
                    eqOfB = TAU_reverseMap.get(B_index - 1).get(tID);
                    if (!bEq2Counter.containsKey(eqOfB)) {
                        bEq2Counter.put(eqOfB, bCnt);
                        thisPGUniques.add(tID);
                        bCnt++;
                    }
                }
            }
            eachPGUniqValToTupID4Approx.add(thisPGUniques);
            allbEq2Counter.add(bEq2Counter);
            allBcnts.add(bCnt);
        }
        
        int curBCnt;
        
        for (int i = 0; i < TAU_XA.size64(); i++) {
            ObjectBigArrayBigList<LongBigArrayBigList> sorted_TAU_LHS = TAU_XA.get(i);
            List<List<Integer>> curECXRes = new ArrayList<>();
            for (LongBigArrayBigList eqC_lhs : sorted_TAU_LHS) {
                List<Integer> curECACounts = new ArrayList<>(Collections.nCopies(allBcnts.get(i), 0));
                for (long tID : eqC_lhs) {
                    curBCnt = allbEq2Counter.get(i).get(TAU_reverseMap.get(B_index - 1).get(tID));
                    curECACounts.set(curBCnt, curECACounts.get(curBCnt) + 1);
                }
                curECXRes.add(curECACounts);
            }
            res.add(curECXRes);
        }
        
        return res;
    }
    
    private List<long[][]> getApproxAdjMatrixForEachPG (List<List<List<Integer>>> occurrances) {
        List res = new ArrayList();
        int[] curVec;
        for (int k = 0; k < occurrances.size(); k++) {
            List<List<Integer>> oneEq = occurrances.get(k);
            int bEqCnt = occurrances.get(k).get(0).size();
            long[][] curRes = new long[bEqCnt][bEqCnt];
            curVec = new int[bEqCnt];
            for (List<Integer> counts : oneEq) {
                for (int i = 0; i < counts.size(); i++) {
                    if (counts.get(i) > 0) {
                        for (int j = 0; j < curVec.length; j++) {
                            curRes[j][i] += (long) curVec[j] * counts.get(i);
                        }
                    }
                }
                for (int i = 0; i < counts.size(); i++) {
                    curVec[i] += counts.get(i);
                }
            }
            res.add(curRes);
        }
        return res;
    }
    
    public static Object deepClone(Object object) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    
    private Map<Long, Set<Long>> mergeChains(List<Map<Long, Set<Long>>> allChains) {
        Map<Long, Set<Long>> merged = new HashMap<>();
        for (Map<Long, Set<Long>> chain : allChains) {
            for (Long key : chain.keySet()) {
                if (!merged.containsKey(key)) {
                    merged.put(key, new HashSet<>());
                }
                merged.get(key).addAll(chain.get(key));
            }
        }
        return merged;
    }
    
    
}