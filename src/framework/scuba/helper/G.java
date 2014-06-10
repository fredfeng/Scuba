package framework.scuba.helper;

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

	public static boolean dumpNumbering = false;

	public static boolean validate = false;

	// flag to tune performance.
	public static boolean tuning = true;

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

	// total time spending on inst locations
	public static long instLocTime = 0;

	// time spending on inst locations per inst edges
	public static long instLocTimePerEdges = 0;

	// number of edges that are instantiated into in the caller
	public static int instToEdges = 0;

	public static String dotOutputPath = "/Users/xwang/xwang/Research/Projects/scuba/Scuba/output/";
}
