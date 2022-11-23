package od;

import od.metanome.ORDERLhsRhs;
import org.apache.lucene.util.OpenBitSet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;

public class MainClass {
    public static ODAlgorithm ODAlgorithm;
    public static String datasetName = "NCVoter Shuff";
    public static String ConfigFileName = "config.txt";
        
    public static String DatasetFileName = "";
    public static String AlgorithmName = "";
    
    public static int MaxRowNumber = 1000000;
    public static int MaxColumnNumber = 1000;
    public static int RunTimeNumber = 1; // set to 1 for now
    
    public static String cvsSplitBy = ",";
    
    public static boolean FullLogging = false;
    
    public static boolean Prune = true; // use or not use pruning rules as in vldb paper
    
    public static boolean FirstTimeRun = true;
    
    public static boolean InterestingnessPrune = false; // default = false
    
    public static long InterestingnessThreshold = 10000000;
    
    public static int topk = 100; // return how many results
    
    public static boolean BidirectionalTrue = false; // false for now (for bidirectional ODs)
    
    public static boolean RankDoubleTrue = true; // keep true
    
    public static boolean ReverseRankingTrue = false; // keep false (for bidirectional ODs)
    
    public static boolean BidirectionalPruneTrue = false; // no need for now (for bidirectional ODs)
    
    public static boolean DoubleAttributes = false; //
    
    public static boolean FindUnionBool = false; // no need for now (for bidirectional ODs)
    
    public static Random Rand;
    
    public static boolean reverseRank = false; // (for bidirectional ODs)
    
    public static int ReverseRankingPercentage = 90; //(not needed) larger than 100 will always reverse it, negative will be regular ranking
    
    public static boolean OnlyCheckConditionalIIOC = false;  // will never consider unconditional cases
    
    public static boolean OnlyExactOCs = false;
    public static boolean CompareBothExactAndApprox = false;
    public static double ApproxThreshold = 1.;  // The score has to be larger than or equal to this
    
    public static List<Boolean> isColumnActive = new ArrayList<>();
    
    public static int approxCardinalityThreshold = 35;
    
    public static List<String> odList = new ArrayList<>(); // results
    public static List<String> finalOCResults = new ArrayList<>(); // final results, used to print in the log file
    public static List<String> latticeWiseData = new ArrayList<>();  // stores runtime + # of OCs at each level
    public static List<Integer> IOCPerLevelCount = new ArrayList<>();  // stores count of all IOCs in each level
    public static long pairBasedScoreTotalTime = 0;
    public static long pathBasedScoreTotalTime = 0;
    
    public static int sats = 0;
    public static int maxSats = 0;
    public static long satRuntime = 0;
    public static long maxSatRuntime = 0;
    public static long reductionRuntime = 0;
    
    public static int numOfFoundIIODs = 0;
    
    public static void main(String[] args) {
        
        printTime();
        
        Rand = new Random(19999);
        
        //TANE      FastOD      ORDER
        //Comma     Tab
    
        String[] fileNames;
        String[] rows;
        String[] cols;
        
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(ConfigFileName));
    
            fileNames = br.readLine().trim().split(",");
            AlgorithmName = br.readLine().trim();
            
            rows = br.readLine().trim().split(",");
            cols = br.readLine().trim().split(",");
            RunTimeNumber = Integer.parseInt(br.readLine().trim());
            
            String lineSeparator = br.readLine().trim();
            cvsSplitBy = lineSeparator;
            
            if (br.readLine().trim().equals("FullLoggingTrue"))
                FullLogging = true;
            
            String pruneS = br.readLine().trim();
            if (pruneS.equals("PruneFalse"))
                Prune = false;
            
            String InterestingnessPruneS = br.readLine().trim();
            if (InterestingnessPruneS.equals("InterestingnessPruneTrue"))
                InterestingnessPrune = true;
            
            InterestingnessThreshold = Long.parseLong(br.readLine().trim());
            
            topk = Integer.parseInt(br.readLine().trim()); // now it's 100 in the input file
            
            String BidirectionalTrueS = br.readLine().trim();
            if (BidirectionalTrueS.equals("BidirectionalTrue"))
                BidirectionalTrue = true;
            
            String RankDoubleTrueS = br.readLine().trim();
            if (!RankDoubleTrueS.equals("RankDoubleTrue"))
                RankDoubleTrue = false;
            
            String ReverseRankingTrueS = br.readLine().trim();
            if (ReverseRankingTrueS.equals("ReverseRankingTrue"))
                ReverseRankingTrue = true;
            
            String BidirectionalPruneTrueS = br.readLine().trim();
            if (BidirectionalPruneTrueS.equals("BidirectionalPruneTrue"))
                BidirectionalPruneTrue = true;
            
            ReverseRankingPercentage = Integer.parseInt(br.readLine().trim());
            
            String OnlyCheckConditionalIIOCS = br.readLine().trim();
            if (OnlyCheckConditionalIIOCS.equals("OnlyConditionalIIOCTrue"))
                OnlyCheckConditionalIIOC = true;
            
            String OnlyCheckExactOCs = br.readLine().trim();
            if (OnlyCheckExactOCs.equals("OnlyExactOCsTrue"))
                OnlyExactOCs = true;
            if (OnlyCheckExactOCs.equals("OnlyExactOCsTrueCompareBoth"))
                CompareBothExactAndApprox = true;
            
            ApproxThreshold = Double.parseDouble(br.readLine().trim());
            
            approxCardinalityThreshold = Integer.parseInt(br.readLine().trim());
            
            String listOfColActivation = br.readLine();
            try {
                if (listOfColActivation != null) {
                    String[] activeColumns = listOfColActivation.trim().split(" ");
                    for (String activeColumn : activeColumns) {
                        isColumnActive.add(Integer.parseInt(activeColumn) == 1);
                    }
                } else {
                    for (int i = 0; i < MaxColumnNumber; i++) {
                        isColumnActive.add(true);
                    }
                }
            } catch (Exception e) {
                for (int i = 0; i < MaxColumnNumber; i++) {
                    isColumnActive.add(true);
                }
            }
            
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
    
        for (int uselessIter = 0; uselessIter < (CompareBothExactAndApprox ? 2 : 1); uselessIter++) {  // Repeat the loop twice if we want to compare
            for (String fileName : fileNames) {
                for (String row : rows) {
                    for (String col : cols) {
                        DatasetFileName = fileName;
                        MaxRowNumber = Integer.parseInt(row);
                        MaxColumnNumber = Integer.parseInt(col);
                
                        TaneAlgorithm taneAlgorithm = new TaneAlgorithm();

                        ODAlgorithm = new ODAlgorithm();
                
                
                        ORDERLhsRhs ORDERAlgorithm = new ORDERLhsRhs();
                
                        System.out.println("Algorithm: " + AlgorithmName);
                        System.out.println("InterestingnessPrune: " + InterestingnessPrune);
                        System.out.println("InterestingnessThreshold: " + InterestingnessThreshold);
                        System.out.println("BidirectionalTrue: " + BidirectionalTrue);
                        System.out.println("BidirectionalPruneTrue: " + BidirectionalPruneTrue);
                
                        long startTime, runTime = 0;
                        try {
                            startTime = System.currentTimeMillis();
                    
                            for (int i = 0; i < RunTimeNumber; i++) {
                        
                                if (AlgorithmName.equals("TANE"))
                                    taneAlgorithm.execute();
                        
                                if (AlgorithmName.equals("FastOD"))
                                    ODAlgorithm.execute();
                        
                                if (AlgorithmName.equals("ORDER"))
                                    ORDERAlgorithm.execute();
                        
                            }
                    
                            long endTime = System.currentTimeMillis();
                            runTime = (endTime - startTime) / RunTimeNumber;
                    
                            System.out.println("Run Time (ms): " + runTime);
    
                            System.out.println("Reduction Runtime (ms) = " + (reductionRuntime / 1_000_000));
                            System.out.println("SATs = " + sats + ", MAX-SATs = " + maxSats);
                            System.out.println("SAT Runtime (ms) = " + (satRuntime / 1_000_000) + ", MAX-SAT Runtime (ms) = " + (maxSatRuntime / 1_000_000));
                    
                        } catch (Exception ex) {
                            System.out.println("Error");
                            ex.printStackTrace();
                        }
                
                        printTime();
                
                        writeMainStatToFile(runTime + " runtime(ms)", false);
                        writeMainStatToFile((pairBasedScoreTotalTime / 1_000_000) + " PairScoreTime(ms)", false);
                        writeMainStatToFile((pathBasedScoreTotalTime / 1_000_000) + " PathScoreTime(ms)", false);
                
                        for (String latticeDetail :
                                latticeWiseData) {
                            writeMainStatToFile(latticeDetail, false);
                        }
                        for (String ocDetail :
                                finalOCResults) {
                            writeMainStatToFile(ocDetail, false);
                        }
                
                        if (numOfFoundIIODs > 0)
                            writeMainStatToFile("Found" + numOfFoundIIODs + "  IIODs", false);
                
                        try {
                            BufferedWriter bw =
                                    new BufferedWriter(new FileWriter("result.txt"));
                    
                            for (String str : odList)
                                bw.write(str + "\n");
                    
                            bw.close();
                        } catch (Exception ex) {
                            System.out.println(ex.getMessage());
                        }
                
                        // Resetting MainClass parameters after each run
                        odList = new ArrayList<>();
                        finalOCResults = new ArrayList<>();
                        latticeWiseData = new ArrayList<>();
                        IOCPerLevelCount = new ArrayList<>();
                        pairBasedScoreTotalTime = 0;
                        pathBasedScoreTotalTime = 0;
                        satRuntime = 0;
                        reductionRuntime = 0;
                        
                        sats = 0;
                        maxSats = 0;
                        maxSatRuntime = 0;
                        numOfFoundIIODs = 0;
                    }
                }
            }
            OnlyExactOCs = true;  // In the (potential) second run only check exact (I)OCs
        }
        
    }
    
    public static void writeMainStatToFile(String textToWrite, boolean doYouWantMeToClearTheFileIfItAlreadyExists) {
        try {
            String filename = "stats/" + DatasetFileName + "-MainStats-" + ODAlgorithm.numberTuples + "-" + ODAlgorithm.numberAttributes + "-" +
                    (MainClass.OnlyExactOCs ? "Ex" : "ExAndApp") +
                    ".txt";
            FileWriter fw;
            if (doYouWantMeToClearTheFileIfItAlreadyExists) {
                fw = new FileWriter(filename, false); // destroy the potentially existing file
            } else {
                fw = new FileWriter(filename, true); //the true will append the new data
            }
            fw.write(textToWrite + "\n"); //appends the string to the file
            fw.close();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }
    
    public static void printTime() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH) + 1; // Note: zero based!
        int day = now.get(Calendar.DAY_OF_MONTH);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int second = now.get(Calendar.SECOND);
        int millis = now.get(Calendar.MILLISECOND);
        
        System.out.printf("%d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
        System.out.println("\n");
    }
    
    public static void printOpenBitSet(OpenBitSet bitSet, int maxLength) {
        for (int i = 0; i < maxLength; i++) {
            if (bitSet.get(i))
                System.out.print(1 + " ");
            else
                System.out.print(0 + " ");
        }
        System.out.println("");
    }
    
    public static void findUnion() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("D:/Code/Datasets/OD/union.txt"));
            
            Set<String> union = new HashSet<String>();
            String str = null;
            while ((str = br.readLine()) != null) {
                union.add(str);
            }
            
            System.out.println("Union Size: " + union.size());
            
            br.close();
        } catch (Exception ex) {
        
        }
    }
}

