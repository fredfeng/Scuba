package framework.scuba.utils;


public class StringUtil {
	
	public static void reportSec(String desc, long start, long end) {
		double difference = (end - start)/1e6;
		System.out.println("[Performance] " + desc + " in " + difference + " ms.");
	}
	
	public static void reportTotalTime(String desc, double time) {
		System.out.println("[Performance] " + desc + " in " + (time/1e6) + " ms.");
	}
	
	public static void reportInfo(String desc) {
		System.out.println("[Performance] " + desc);
	}

}
