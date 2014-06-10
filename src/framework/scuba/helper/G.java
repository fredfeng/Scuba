package framework.scuba.helper;

/**
 * some global environments.
 * 
 * @author yufeng
 * 
 */
public class G {

	public static boolean debug = false;

	public static int number = 2400;

	// more dbg info
	public static boolean debug1 = false;

	public static boolean dump = false;
	
	//flag to tune performance.
	public static boolean tuning = true;

	public static int count = 0;
	
	//how many scc do we actuall pass.
	public static int countScc = 0;

	public static int step = 1;
	
	//total time spending on inst Cst
	public static long instCstTime = 0;
	
	//total time spending on gen Cst
	public static long genCstTime = 0;

	public static String dotOutputPath = "/home/yufeng/research/Scuba/output/";
}
