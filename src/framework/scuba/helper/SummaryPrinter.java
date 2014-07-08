package framework.scuba.helper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.microsoft.z3.BoolExpr;

import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;
import framework.scuba.domain.AbsMemLoc;
import framework.scuba.domain.AbstractHeap;
import framework.scuba.domain.AccessPath;
import framework.scuba.domain.AllocElem;
import framework.scuba.domain.FieldElem;
import framework.scuba.domain.HeapObject;
import framework.scuba.domain.LocalVarElem;
import framework.scuba.domain.P2Set;
import framework.scuba.domain.ParamElem;
import framework.scuba.domain.RetElem;
import framework.scuba.domain.StaticElem;
import framework.scuba.domain.SummariesEnv;
import framework.scuba.domain.Summary;
import framework.scuba.utils.StringUtil;

public class SummaryPrinter {
	
	public void dumpSummary4Method(jq_Method meth) {
		System.out.println("Summary for method: " + meth.getName());
		System.out.println("**************************************");
		Set<jq_Type> allocs = new HashSet<jq_Type>();
		List<jq_Type> allocList = new ArrayList<jq_Type>();

		Set<Register> fieldsBase = new HashSet<Register>();
		List<Register> fieldsBaseList = new ArrayList<Register>();

		Set<jq_Field> fieldsAccess = new HashSet<jq_Field>();
		List<jq_Field> fieldsAccList = new ArrayList<jq_Field>();

		Set<Register> locals = new HashSet<Register>();

		ControlFlowGraph cfg = meth.getCFG();
		String params = "";
		RegisterFactory rf = cfg.getRegisterFactory();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			Register v = rf.get(zIdx);
			params = params + " " + v;
		}

		for (Register v : meth.getLiveRefVars()) {
			if (!params.contains(v.toString()))
				locals.add(v);
		}

		for (BasicBlock bb : cfg.reversePostOrder()) {
			for (Quad q : bb.getQuads()) {
				Operator op = q.getOperator();
				if (op instanceof New) {
					allocs.add(New.getType(q).getType());
					allocList.add(New.getType(q).getType());
				}

				if (op instanceof NewArray) {
					allocs.add(NewArray.getType(q).getType());
					allocList.add(NewArray.getType(q).getType());

				}

				if (op instanceof MultiNewArray) {
					allocs.add(MultiNewArray.getType(q).getType());
					allocList.add(MultiNewArray.getType(q).getType());

				}

				if (op instanceof Putfield) {
					fieldsBase.add(((RegisterOperand) Putfield.getBase(q))
							.getRegister());
					fieldsBaseList.add(((RegisterOperand) Putfield.getBase(q))
							.getRegister());

					fieldsAccess.add(Putfield.getField(q).getField());
					fieldsAccList.add(Putfield.getField(q).getField());

				}

				if (op instanceof Getfield) {
					fieldsBase.add(((RegisterOperand) Getfield.getBase(q))
							.getRegister());
					fieldsBaseList.add(((RegisterOperand) Getfield.getBase(q))
							.getRegister());

					fieldsAccess.add(Getfield.getField(q).getField());
					fieldsAccList.add(Getfield.getField(q).getField());
				}

				if (op instanceof Putstatic) {
					fieldsAccess.add(Putstatic.getField(q).getField());
					fieldsAccList.add(Putstatic.getField(q).getField());
				}

				if (op instanceof Getstatic) {
					fieldsAccess.add(Getstatic.getField(q).getField());
					fieldsAccList.add(Getstatic.getField(q).getField());
				}

			}
		}

		System.out.println("PARAM Set: " + params);

		System.out.println("Local Set: " + locals);

		System.out.println("Alloc Set: " + allocs);
		System.out.println("Alloc List: " + allocList);

		System.out.println("Field base Set: " + fieldsBase);
		System.out.println("Field base List: " + fieldsBaseList);

		System.out.println("Field access List: " + fieldsAccList);
		System.out.println("Field access Set: " + fieldsAccess);

		System.out.println("**************************************");
	}
	
	public void dumpSummaryToFile(AbstractHeap absHeap, String count) {
		absHeap.dumpHeapToFile(count);
		// absHeap.dumpHeapMappingToFile(count);
	}

	public void dumpSummaryMappingToFile(AbstractHeap absHeap, String count) {
		// absHeap.dumpHeapToFile(count);
		absHeap.dumpHeapMappingToFile(count);
	}

	public void dumpAllMemLocsHeapToFile(AbstractHeap absHeap, String count) {
		absHeap.dumpAllMemLocsHeapToFile(count);
	}
	
	public void printCalleeHeapInfo(AbstractHeap absHeap, String s) {
		int param2Alloc = 0;
		int param2AP = 0;
		int static2Alloc = 0; // can avoid
		int static2AP = 0;
		int local2Alloc = 0; // can avoid
		int local2AP = 0;
		int ret2Alloc = 0;
		int ret2AP = 0;
		int ap2AP = 0;
		int ap2Alloc = 0;
		int alloc2Alloc = 0;
		int alloc2AP = 0;
		int total = 0;

		for (Pair<AbsMemLoc, FieldElem> pair : absHeap.locToP2Set.keySet()) {
			AbsMemLoc src = pair.val0;
			P2Set tgts = absHeap.locToP2Set.get(pair);
			for (HeapObject tgt : tgts.keySet()) {
				if (src instanceof ParamElem && tgt instanceof AllocElem) {
					param2Alloc++;
				} else if (src instanceof ParamElem
						&& tgt instanceof AccessPath) {
					param2AP++;
				} else if (src instanceof StaticElem
						&& tgt instanceof AllocElem) {
					static2Alloc++;
				} else if (src instanceof StaticElem
						&& tgt instanceof AccessPath) {
					static2AP++;
				} else if (src instanceof LocalVarElem
						&& tgt instanceof AllocElem) {
					local2Alloc++;
				} else if (src instanceof LocalVarElem
						&& tgt instanceof AccessPath) {
					local2AP++;
				} else if (src instanceof RetElem && tgt instanceof AllocElem) {
					ret2Alloc++;
				} else if (src instanceof RetElem && tgt instanceof AccessPath) {
					ret2AP++;
				} else if (src instanceof AccessPath
						&& tgt instanceof AllocElem) {
					ap2Alloc++;
				} else if (src instanceof AccessPath
						&& tgt instanceof AccessPath) {
					ap2AP++;
				} else if (src instanceof AllocElem && tgt instanceof AllocElem) {
					alloc2Alloc++;
				} else if (src instanceof AllocElem
						&& tgt instanceof AccessPath) {
					alloc2AP++;
				} else {
					StringUtil.reportInfo(s + ": src " + src.getClass());
					StringUtil.reportInfo(s + ": tgt " + tgt.getClass());
					assert false;
				}
			}
		}
		total = param2Alloc + param2AP + static2Alloc + static2AP + local2Alloc
				+ local2AP + ret2Alloc + ret2AP + ap2Alloc + ap2AP
				+ alloc2Alloc + alloc2AP;
		if (ap2AP > 100) {
			StringUtil.reportInfo(s + ": -----------------------------------");
			StringUtil.reportInfo(s + ": parameter --> Alloc: " + param2Alloc
					+ " out of " + total);
			StringUtil.reportInfo(s + ": parameter --> AccessPath: " + param2AP
					+ " out of " + total);
			StringUtil.reportInfo(s + ": static --> Alloc: " + static2Alloc
					+ " out of " + total);
			StringUtil.reportInfo(s + ": static --> AccessPath: " + static2AP
					+ " out of " + total);
			StringUtil.reportInfo(s + ": local --> Alloc: " + local2Alloc
					+ " out of " + total);
			StringUtil.reportInfo(s + ": local --> AccessPath: " + local2AP
					+ " out of " + total);
			StringUtil.reportInfo(s + ": ret --> Alloc: " + ret2Alloc
					+ " out of " + total);
			StringUtil.reportInfo(s + ": ret --> AccessPath: " + ret2AP
					+ " out of " + total);
			StringUtil.reportInfo(s + ": AccessPath --> Alloc: " + ap2Alloc
					+ " out of " + total);
			StringUtil.reportInfo(s + ": AccessPath --> AccessPath: " + ap2AP
					+ " out of " + total);
			StringUtil.reportInfo(s + ": Alloc --> Alloc: " + alloc2Alloc
					+ " out of " + total);
			StringUtil.reportInfo(s + ": Alloc --> AccessPath: " + alloc2AP
					+ " out of " + total);
		}
	}
	
	public void dumpStatistics() {
		StringBuilder b = new StringBuilder("");
		Map<jq_Method, Summary> sums = SummariesEnv.v().getSums();
		int total_all = 0;
		int t_all = 0;
		int f_all = 0;
		int other_all = 0;
		for (jq_Method m : sums.keySet()) {
			int total = 0;
			int t = 0;
			int f = 0;
			int other = 0;
			Map<Pair<AbsMemLoc, FieldElem>, P2Set> absHeap = sums.get(m)
					.getAbsHeap().getHeap();
			for (Pair<AbsMemLoc, FieldElem> pair : absHeap.keySet()) {
				P2Set p2set = absHeap.get(pair);
				total += p2set.size();
				total_all += p2set.size();
				for (HeapObject hObj : p2set.keySet()) {
					BoolExpr cst = p2set.get(hObj);
					if (ConstraintManager.isTrue(cst)) {
						t++;
						t_all++;
					} else if (ConstraintManager.isFalse(cst)) {
						f++;
						f_all++;
					} else {
						other++;
						other_all++;
					}
				}
				b.append("----------------------------------------------\n");
				b.append("Method: " + m + "\n");
				b.append("Total: " + total + "\n");
				b.append("True cst: " + t + "\n");
				b.append("False cst: " + f + "\n");
				b.append("Other cst: " + other + "\n");
			}
		}

		b.append("----------------------------------------------\n");
		b.append("Total: " + total_all + "\n");
		b.append("True cst: " + t_all + "\n");
		b.append("False cst: " + f_all + "\n");
		b.append("Other cst: " + other_all + "\n");

		System.out.println(b.toString());
//		StringUtil.reportTotalTime("Total Time on Library: ", libTime);
//		StringUtil.reportTotalTime("Total Time on App: ", appTime);
	}
}
