package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Compiler.Quad.Quad;

public class ProgPointFactory {

	public final static Map<Quad, ProgPoint> ppFactory = new HashMap<Quad, ProgPoint>();

	public final static Map<Quad, Integer> ppToId = new HashMap<Quad, Integer>();

	// numbers of program points start from 1
	public static int maxNum;

	public static ProgPoint getProgPoint(Quad stmt) {
		ProgPoint ret = ppFactory.get(stmt);
		if (ret == null) {
			ret = new ProgPoint(stmt, ++maxNum);
			updateMap(stmt, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(Quad stmt, int number, ProgPoint pp) {
		ppFactory.put(stmt, pp);
		ppToId.put(stmt, number);
	}

	public static void clear() {
		ppFactory.clear();
		ppToId.clear();
		maxNum = 0;
	}
}
