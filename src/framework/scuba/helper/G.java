package framework.scuba.helper;

import java.util.HashMap;
import java.util.Map;

import framework.scuba.domain.Summary;

/**
 * some global environments.
 * 
 * @author yufeng
 * 
 */
public class G {

	public static boolean info = false;

	public static boolean debug4Sum = false;

	public static boolean debug4Invoke = false;

	public static boolean debug4AbsHeap = false;

	public static boolean dump = false;

	public static boolean validate = false;

	public static boolean dbgPermission = false;

	public static boolean dbgQuery = false;

	public static int mId = 0;
	public static boolean dbgAntlr = false;
	public static boolean dbgInvoke = false;
	public static boolean dbgInstn = false;
	public static Map<Summary, Integer> IdMapping = new HashMap<Summary, Integer>();
	public static boolean dbgUndet = false;

	public static boolean dbgFilter = false;
	public static boolean dbgCache = false;

	// flag to tune performance.
	public static boolean tuning = true;

	public static boolean stat = false;

	public static int count = 0;

	// how many scc do we actuall pass.
	public static int countScc = 0;

	public static int step = 1;

	// total time spending on inst Cst
	public static long instCstTime = 0;

	// total time spending on gen Cst
	public static long genCstTime = 0;

	// total time spending on inst edges
	public static long instEdgeTime = 0;

	// total time spending on constaint operations
	public static long cstOpTime = 0;

	// max constraint length.
	public static int maxCst = 0;

	// total time spending on inst locations
	public static long instLocTime = 0;
	
	public static long equalsTime = 0;

	public static long unionTime = 0;
	public static long interTime = 0;
	public static long subTime = 0;
	public static long extractTime = 0;
	public static long instTime = 0;
	public static long simTime = 0;
	public static long genTime = 0;
	public static long instSubTime = 0;
	public static long liftTime = 0;
	public static long exprTime = 0;
	public static long eqTime = 0;
	public static long cst1 = 0;
	public static long cst2 = 0;
	public static long cst3 = 0;

	public static long genEqTime = 0;

	public static long geTime = 0;
	public static long leTime = 0;

	
	// time spending on inst locations per inst edges
	public static long instLocTimePerEdges = 0;

	// number of edges that are instantiated into in the caller
	public static int instToEdges = 0;

	// public static String dotOutputPath =
	// "/home/yufeng/research/Scuba/output/";
	public static String dotOutputPath = "/Users/xwang/xwang/Research/Projects/scuba/Scuba/output/";

	public static long instCstSubTime;
}
