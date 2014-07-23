package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import chord.util.tuple.object.Pair;

public class ContextFactory {

	public final static Map<Pair<ProgPoint, Context>, Context> ctxFactory = new HashMap<Pair<ProgPoint, Context>, Context>();

	public final static Map<Pair<ProgPoint, Context>, Integer> ctxToId = new HashMap<Pair<ProgPoint, Context>, Integer>();

	// numbers of contexts start from 1
	public static int maxNum;

	public static Context getContext(ProgPoint point, Context prevCtx) {
		Context ret = ctxFactory.get(new Pair<ProgPoint, Context>(point,
				prevCtx));
		if (ret == null) {
			ret = new Context(point, prevCtx, ++maxNum);
			updateMap(point, prevCtx, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(ProgPoint point, Context prevCtx, int number,
			Context ctx) {
		Pair<ProgPoint, Context> pair = new Pair<ProgPoint, Context>(point,
				prevCtx);
		ctxFactory.put(pair, ctx);
		ctxToId.put(pair, number);
	}

	public static void clear() {
		ctxFactory.clear();
		ctxToId.clear();
		maxNum = 0;
	}
}
