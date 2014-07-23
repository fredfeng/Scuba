package framework.scuba.helper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.domain.AbsHeap;
import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.Env;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.ParamElem;
import framework.scuba.domain.RetElem;
import framework.scuba.domain.StaticFieldElem;

public class AbsHeapPrinter {

	public static void dumpHeapToFile(AbsHeap absHeap, String count) {
		StringBuilder b = new StringBuilder("digraph AbstractHeap {\n");
		b.append("  rankdir = LR;\n");

		Set<AbsMemLoc> allLocs = new HashSet<AbsMemLoc>();

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			allLocs.add(pair.val0);
			for (HeapObject hObj : absHeap.locToP2Set.get(pair).keySet()) {
				allLocs.add(hObj);
			}
		}

		for (AbsMemLoc loc : allLocs) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticFieldElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof RetElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=diamond,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else {
				assert false : "wired things! Unknow memory location";
			}
		}

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			AbsMemLoc loc = pair.val0;
			FieldElem f = pair.val1;
			P2Set p2Set = absHeap.locToP2Set.get(pair);
			for (HeapObject hObj : p2Set.keySet()) {
				BoolExpr cst = p2Set.get(hObj);
				b.append("  ").append("\"" + loc + "\"");
				b.append(" -> ").append("\"" + hObj + "\"")
						.append(" [label=\"");
				b.append("(" + f + "," + cst + ")");
				b.append("\"]\n");
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + ".dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public static void dumpPropToFile(AbsHeap absHeap, String count) {
		StringBuilder b = new StringBuilder("digraph AbstractHeap {\n");
		b.append("  rankdir = LR;\n");

		Set<AbsMemLoc> allLocs = new HashSet<AbsMemLoc>();

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			if (!Env.toProp(pair.val0)) {
				continue;
			}
			allLocs.add(pair.val0);
			for (HeapObject hObj : absHeap.locToP2Set.get(pair).keySet()) {
				allLocs.add(hObj);
			}
		}

		for (AbsMemLoc loc : allLocs) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticFieldElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=triangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof RetElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=diamond,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else {
				assert false : "Unknow memory location";
			}
		}

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			AbsMemLoc loc = pair.val0;
			if (!Env.toProp(loc)) {
				continue;
			}
			FieldElem f = pair.val1;
			P2Set p2Set = absHeap.locToP2Set.get(pair);
			for (HeapObject hObj : p2Set.keySet()) {
				BoolExpr cst = p2Set.get(hObj);
				b.append("  ").append("\"" + loc + "\"");
				b.append(" -> ").append("\"" + hObj + "\"")
						.append(" [label=\"");
				b.append("(" + f + "," + cst + ")");
				b.append("\"]\n");
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + ".dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public static void dumpSumToFile(AbsHeap absHeap, String count) {
		StringBuilder b = new StringBuilder("digraph AbstractHeap {\n");
		b.append("  rankdir = LR;\n");

		Set<AbsMemLoc> allLocs = new HashSet<AbsMemLoc>();

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			if (!pair.val0.isArgDvd()) {
				continue;
			}
			allLocs.add(pair.val0);
			for (HeapObject hObj : absHeap.locToP2Set.get(pair).keySet()) {
				allLocs.add(hObj);
			}
		}

		for (AbsMemLoc loc : allLocs) {
			if (loc instanceof AccessPath) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=circle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof AllocElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=rectangle,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof StaticFieldElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof LocalVarElem) {
				assert false : "LocalVarElem should not appear.";
			} else if (loc instanceof ParamElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=oval,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else if (loc instanceof RetElem) {
				b.append("  ").append("\"" + loc + "\"");
				b.append(" [shape=diamond,label=\"");
				b.append(loc.toString());
				b.append("\"];\n");
			} else {
				assert false : "wired things! Unknow memory location";
			}
		}

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			AbsMemLoc loc = pair.val0;
			if (!loc.isArgDvd()) {
				continue;
			}
			FieldElem f = pair.val1;
			P2Set p2Set = absHeap.locToP2Set.get(pair);
			for (HeapObject hObj : p2Set.keySet()) {
				BoolExpr cst = p2Set.get(hObj);
				b.append("  ").append("\"" + loc + "\"");
				b.append(" -> ").append("\"" + hObj + "\"")
						.append(" [label=\"");
				b.append("(" + f + "," + cst + ")");
				b.append("\"]\n");
			}
		}

		b.append("}\n");

		try {
			BufferedWriter bufw = new BufferedWriter(new FileWriter(
					G.dotOutputPath + count + ".dot"));
			bufw.write(b.toString());
			bufw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
