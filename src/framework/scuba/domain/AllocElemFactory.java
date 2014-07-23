package framework.scuba.domain;

import java.util.HashMap;
import java.util.Map;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import chord.util.tuple.object.Trio;

public class AllocElemFactory {

	public final static Map<Trio<Quad, jq_Type, Context>, AllocElem> allocElemFactory = new HashMap<Trio<Quad, jq_Type, Context>, AllocElem>();

	public final static Map<Trio<Quad, jq_Type, Context>, Integer> allocElemToId = new HashMap<Trio<Quad, jq_Type, Context>, Integer>();

	// numbers of allocElems start from 1
	public static int maxNum;

	public static AllocElem getAllocElem(Quad stmt, jq_Type type, Context ctx) {
		AllocElem ret = allocElemFactory.get(new Trio<Quad, jq_Type, Context>(
				stmt, type, ctx));
		if (ret == null) {
			ret = new AllocElem(stmt, ctx, type, ++maxNum);
			updateMap(stmt, ctx, type, maxNum, ret);
		}
		return ret;
	}

	private static void updateMap(Quad stmt, Context ctx, jq_Type type,
			int number, AllocElem allocElem) {
		Trio<Quad, jq_Type, Context> trio = new Trio<Quad, jq_Type, Context>(
				stmt, type, ctx);
		allocElemFactory.put(trio, allocElem);
		allocElemToId.put(trio, number);
	}

	public static void clear() {
		allocElemFactory.clear();
		allocElemToId.clear();
		maxNum = 0;
	}
}
