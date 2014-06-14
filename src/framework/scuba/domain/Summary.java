package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operand.ParamListOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operand.TypeOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.ALoad;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEINTERFACE_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKESTATIC_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKESTATIC_V;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEVIRTUAL_A;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
import joeq.Compiler.Quad.Operator.Invoke.InvokeStatic;
import joeq.Compiler.Quad.Operator.Invoke.InvokeVirtual;
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.Move.MOVE_A;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;

import framework.scuba.analyses.alias.SummaryBasedAnalysis;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.AbstractHeap.VariableType;
import framework.scuba.helper.ConstraintManager;
import framework.scuba.helper.G;
import framework.scuba.utils.StringUtil;

/**
 * Representing the summary for a method. Now it only contains abstractHeap.
 * 
 * @author yufeng
 * 
 */
public class Summary {

	public int times = 0;

	private jq_Method method;

	private AbstractHeap absHeap;

	public static int aloadCnt = 0;

	public static int astoreCnt = 0;

	public static int aNewArrayCnt = 0;

	public static int aNewMulArrayCnt = 0;

	public static int castCnt = 0;

	// (call site, callee method) --> memory location instantiation
	// invoke stmt includes: InvokeVirtual, InvokeStatic, and InvokeInterface
	private Map<Pair<Quad, jq_Method>, MemLocInstantiation> methCallToMemLocInstantiation;

	// finish current summary.
	private boolean terminated;

	// numbering counter
	protected int currNumCounter = 0;

	protected int numToAssign = 0;

	// used for numbering
	protected boolean isInSCC = false;

	// parameter list used for instantiating
	// once initialized, never changed
	protected List<ParamElem> formals;

	// return value list
	protected RetElem retValue;

	// heap for the whole summary has changed?
	protected boolean changed = false;

	public boolean isChanged() {
		return changed;
	}

	public void setChanged(boolean isChanged) {
		this.changed = isChanged;
	}

	public Summary(jq_Method meth) {
		method = meth;
		absHeap = new AbstractHeap(meth);
		methCallToMemLocInstantiation = new HashMap<Pair<Quad, jq_Method>, MemLocInstantiation>();
		if (G.dump) {
			this.dumpSummary4Method(meth);
		}
	}

	// initialize the paramList
	// this will be done ONLY once!
	public void initFormals() {
		formals = new ArrayList<ParamElem>();
	}

	// fill the paramList from left to right, one by one
	// MUST keep the proper sequence!
	// every parameter in the list will only be translated once!
	public void fillFormals(jq_Class clazz, jq_Method method, Register param) {
		formals.add(absHeap.getParamElem(clazz, method, param));
	}

	// get the paramList, which will used in the instantiation
	public List<ParamElem> getFormals() {
		return formals;
	}

	public void setRetValue(RetElem retValue) {
		this.retValue = retValue;
	}

	public RetElem getRetValue() {
		return retValue;
	}

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

	public void dumpSummaryToFile(String count) {
		absHeap.dumpHeapToFile(count);
	}

	public void dumpAllMemLocsHeapToFile(String count) {
		absHeap.dumpAllMemLocsHeapToFile(count);
	}

	public void dumpNumberingHeap(String count) {
		absHeap.dumpHeapNumberingToFile(count);
	}

	public void validate() {
		absHeap.validate();
	}

	public boolean isTerminated() {
		return terminated;
	}

	public void setTerminated(boolean terminated) {
		this.terminated = terminated;
	}

	public AbstractHeap getAbstractHeap() {
		return absHeap;
	}

	public jq_Method getMethod() {
		return method;
	}

	public boolean handleStmt(Quad quad, int numToAssign, boolean isInSCC) {
		absHeap.markChanged(false);
		this.numToAssign = numToAssign;
		this.isInSCC = isInSCC;
		if (G.dbgSCC) {
			StringUtil.reportInfo("weak update size: " + quad);
		}
		quad.accept(qv);
		this.currNumCounter = absHeap.getMaxNumber();
		return absHeap.isChanged();
	}

	public static int tmp = 0;
	public static int tmp1 = 0;
	public static int tmp2 = 0;

	QuadVisitor qv = new QuadVisitor.EmptyVisitor() {

		// no-op.
		public void visitALength(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		// perform array smashing. Use assign to handle array store/load.
		// y = x[1];
		public void visitALoad(Quad stmt) {
			// TODO
			Summary.aloadCnt++;

			jq_Method meth = stmt.getMethod();
			if (ALoad.getDest(stmt) instanceof RegisterOperand) {
				RegisterOperand rhs = (RegisterOperand) ALoad.getBase(stmt);
				RegisterOperand lhs = (RegisterOperand) ALoad.getDest(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				boolean flag = false;

				flag = absHeap.handleALoadStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhs.getRegister(), rvt,
						numToAssign, isInSCC);

				absHeap.markChanged(flag);

			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}
		}

		// x[1] = y
		public void visitAStore(Quad stmt) {
			// TODO
			Summary.astoreCnt++;
			jq_Method meth = stmt.getMethod();

			if (AStore.getValue(stmt) instanceof RegisterOperand) {
				RegisterOperand lhs = (RegisterOperand) AStore.getBase(stmt);
				RegisterOperand rhs = (RegisterOperand) AStore.getValue(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				boolean flag = false;

				flag = absHeap.handleAStoreStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhs.getRegister(), rvt,
						numToAssign, isInSCC);

				absHeap.markChanged(flag);

			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction");
				}
			}
		}

		// no-op.
		public void visitBinary(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		// no-op.
		public void visitBoundsCheck(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		// no-op.
		public void visitBranch(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		// no sure whether we should mark this as no op.
		public void visitCheckCast(Quad stmt) {
			Summary.castCnt++;
		}

		// v1 = v2.f
		public void visitGetfield(Quad stmt) {
			// TODO
			FieldOperand field = Getfield.getField(stmt);

			if (field.getField().getType() instanceof jq_Reference) {
				assert (stmt.getOperator() instanceof Getfield);
				RegisterOperand lhs = Getfield.getDest(stmt);
				RegisterOperand rhsBase = (RegisterOperand) Getfield
						.getBase(stmt);
				jq_Method meth = stmt.getMethod();
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhsBase.getRegister());

				boolean flag = false;
				flag = absHeap.handleLoadStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhsBase.getRegister(),
						field.getField(), rvt, numToAssign, isInSCC);

				absHeap.markChanged(flag);

			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}
		}

		// v = A.f.
		public void visitGetstatic(Quad stmt) {
			// TODO
			FieldOperand field = Getstatic.getField(stmt);

			if (field.getField().getType() instanceof jq_Reference) {
				jq_Method meth = stmt.getMethod();
				RegisterOperand lhs = Getstatic.getDest(stmt);
				jq_Class encloseClass = field.getField().getDeclaringClass();
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());

				boolean flag = false;
				flag = absHeap.handleStatLoadStmt(meth.getDeclaringClass(),
						meth, lhs.getRegister(), lvt, encloseClass,
						field.getField(), numToAssign, isInSCC);

				absHeap.markChanged(flag);

			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}
		}

		// no-op.
		public void visitInstanceOf(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		public void visitInvoke(Quad stmt) {
			tmp++;

			long startInstCallsite = System.nanoTime();

			// get rhs in the factory (maybe we do not need to)
			assert (stmt.getOperator() instanceof Invoke);
			// the callsite's belonging method
			jq_Method meth = stmt.getMethod();
			// retrieve the summaries of the potential callees
			// getSumCstPairList returns only the summaries for methods that
			// have been analyzed
			List<Pair<Summary, BoolExpr>> calleeSumCstPairs = getSumCstPairList(stmt);

			if (G.dbgMatch) {
				StringUtil.reportInfo("Sunny -- Invoke progress: [ CG: "
						+ SummaryBasedAnalysis.cgProgress + " BB: "
						+ IntraProcSumAnalysis.bbProgress + " ]");
				StringUtil.reportInfo("Sunny -- Invoke progress: "
						+ "potential callee sums size: "
						+ calleeSumCstPairs.size());
			}

			if (G.dbgSCC) {
				StringUtil.reportInfo("in the invoke: " + tmp + " " + stmt
						+ " " + calleeSumCstPairs.size());
			}

			if (G.tuning)
				StringUtil.reportInfo("handle callsite: " + stmt + " In "
						+ stmt.getMethod() + " Size: "
						+ calleeSumCstPairs.size());

			if (G.debug4Invoke) {
				System.out
						.println("[Debug4Invoke] Retrieving summaries for callees...");
				System.out.println("[Debug4Invoke] Size: "
						+ calleeSumCstPairs.size());
			}

			int count = 0;
			// iterate all summaries of all the potential callees
			for (Pair<Summary, BoolExpr> calleeSumCst : calleeSumCstPairs) {
				count++;
				tmp1++;
				if (G.dbgSCC) {
					StringUtil.reportInfo("tmp1: " + tmp1
							+ "in the callee sum: " + count + " out of "
							+ calleeSumCstPairs.size());
				}
				// the summary of the callee
				Summary calleeSum = calleeSumCst.val0;
				// the constraint for calling that callee
				BoolExpr hasTypeCst = calleeSumCst.val1;

				// we should only get non-null summary for getSumCstPairList
				assert (calleeSum != null) : "null summary!";
				// we should only get summaries of methods that have been
				// analyzed which is guaranteed by getSumCstPairList
				assert (calleeSum.getFormals() != null) : "null formals list!";
				assert (hasTypeCst != null) : "invalid has type constraint!";

				// get the memory location instantiation for the callee
				MemLocInstantiation memLocInstn = methCallToMemLocInstantiation
						.get(new Pair<Quad, jq_Method>(stmt, calleeSum
								.getMethod()));
				if (G.dbgRet) {
					StringUtil.reportInfo(" get the mem loc instn");
					if (memLocInstn == null) {
						StringUtil.reportInfo("it is a null!");
					} else {
						StringUtil.reportInfo("------");
						memLocInstn.print();
					}
				}
				// if has not been cached
				if (memLocInstn == null) {
					memLocInstn = new MemLocInstantiation(meth, stmt,
							calleeSum.getMethod());
					methCallToMemLocInstantiation.put(
							new Pair<Quad, jq_Method>(stmt, calleeSum
									.getMethod()), memLocInstn);
					// fill the formal-to-actual mapping
					ParamListOperand actuals = Invoke.getParamList(stmt);
					List<StackObject> actualsMemLoc = new ArrayList<StackObject>();
					// mapping the actuals in the call site of the caller to the
					// locations in the caller's heap
					for (int i = 0; i < actuals.length(); i++) {
						Register v = actuals.get(i).getRegister();
						if (getVarType(meth, v) == VariableType.PARAMEMTER) {
							actualsMemLoc.add(absHeap.getParamElem(
									meth.getDeclaringClass(), meth, v));
						} else if (getVarType(meth, v) == VariableType.LOCAL_VARIABLE) {
							actualsMemLoc.add(absHeap.getLocalVarElem(
									meth.getDeclaringClass(), meth, v));
						} else {
							// actuals can be primitives, we use constants to
							// denote those (we do not map formals to constants)
							actualsMemLoc.add(PrimitiveElem.getPrimitiveElem());
						}
					}
					// fill the formal-to-actual mapping
					assert (calleeSum.getFormals().size() == actualsMemLoc
							.size()) : "unmatched actuals and formals list!";

					memLocInstn.initFormalToActualMapping(
							calleeSum.getFormals(), actualsMemLoc);
					// fill the return-value mapping
					// ONLY for x = v.foo(a1, a2)
					Operator opr = stmt.getOperator();
					if (opr instanceof INVOKESTATIC_A
							|| opr instanceof INVOKEVIRTUAL_A
							|| opr instanceof INVOKEINTERFACE_A) {

						if (G.dbgRet) {
							StringUtil.reportInfo("init the return mapping");
						}

						RegisterOperand ro = Invoke.getDest(stmt);
						StackObject sObj = getMemLocation(
								meth.getDeclaringClass(), meth,
								ro.getRegister());
						assert (sObj != null) : "Fails to locate the right heap obj.";
						memLocInstn.initReturnToLHS(calleeSum.getRetValue(),
								sObj);
					}
				}
				if (G.tuning)
					StringUtil.reportInfo("calleeSum Info: "
							+ calleeSum.getMethod());
				// instantiation the edges in the callee's heap
				if (G.dbgSCC) {
					StringUtil
							.reportInfo("[before handling invoke] weak update size: ");
				}

				boolean flag = false;

				if (G.dbgMatch) {

				}

				if (SummariesEnv.v().useNumbering()) {
					flag = absHeap.handleInvokeStmt(meth.getDeclaringClass(),
							meth, stmt.getID(), calleeSum.getAbsHeap(),
							memLocInstn, hasTypeCst, numToAssign, isInSCC);
				} else {
					flag = absHeap.handleInvokeStmtNoNumbering(
							meth.getDeclaringClass(), meth, stmt.getID(),
							calleeSum.getAbsHeap(), memLocInstn, hasTypeCst,
							numToAssign, isInSCC);
				}

				absHeap.markChanged(flag);
				// if (tmp == 1281) {
				// absHeap.dumpHeapNumberingToFile("$caller" + count);
				// }
			}
			if (G.debug4Sum) {
				if (calleeSumCstPairs.isEmpty()) {
					System.out
							.println("[Debug4Sum] Either there is no available summaries "
									+ "or it is not a processable statement!");
				}
			}

			long endInstCallsite = System.nanoTime();
			if (G.tuning)
				StringUtil.reportSec("Time to instantiate callsite: " + stmt,
						startInstCallsite, endInstCallsite);

		}

		// no sure whether we should mark this as no op.
		public void visitMemLoad(Quad stmt) {
			System.out.println(stmt);
			assert false : "MemLoad";
		}

		// no sure whether we should mark this as no op.
		public void visitMemStore(Quad stmt) {
			System.out.println(stmt);
			assert false : "MemStore";
		}

		// no-op.
		public void visitMonitor(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		// v1 = v2
		public void visitMove(Quad stmt) {
			jq_Method meth = stmt.getMethod();
			// 1. the first clause make sure only if it's ref type.
			// 2. the second clause is to ignore string assignment like x="Hi"
			// The question is, do we need to handle string operation? TODO
			if ((stmt.getOperator() instanceof MOVE_A)
					&& (Move.getSrc(stmt) instanceof RegisterOperand)) {
				RegisterOperand rhs = (RegisterOperand) Move.getSrc(stmt);
				RegisterOperand lhs = (RegisterOperand) Move.getDest(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());
				boolean flag = false;
				flag = absHeap.handleAssignStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhs.getRegister(), rvt,
						numToAssign, isInSCC);

				absHeap.markChanged(flag);
			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}
		}

		// v1 = new A();
		public void visitNew(Quad stmt) {
			assert (stmt.getOperator() instanceof New);

			jq_Method meth = stmt.getMethod();
			TypeOperand to = New.getType(stmt);
			RegisterOperand rop = New.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			boolean flag = false;
			flag = absHeap.handleNewStmt(stmt.getMethod().getDeclaringClass(),
					meth, rop.getRegister(), vt, to.getType(), stmt.getID(),
					numToAssign, isInSCC);

			absHeap.markChanged(flag);
		}

		// v = new Node[10][10]
		public void visitMultiNewArray(Quad stmt) {
			Summary.aNewMulArrayCnt++;
			assert (stmt.getOperator() instanceof MultiNewArray);
			jq_Method meth = stmt.getMethod();
			TypeOperand to = MultiNewArray.getType(stmt);
			RegisterOperand rop = MultiNewArray.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());
			ParamListOperand plo = MultiNewArray.getParamList(stmt);
			boolean flag = false;
			flag = absHeap.handleMultiNewArrayStmt(meth.getDeclaringClass(),
					meth, rop.getRegister(), vt, to.getType(), plo.length(),
					stmt.getID(), numToAssign, isInSCC);

			absHeap.markChanged(flag);
		}

		// v = new Node[10];
		public void visitNewArray(Quad stmt) {
			Summary.aNewArrayCnt++;
			assert (stmt.getOperator() instanceof NewArray);
			jq_Method meth = stmt.getMethod();
			TypeOperand to = NewArray.getType(stmt);
			RegisterOperand rop = NewArray.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			boolean flag = false;

			flag = absHeap.handleNewArrayStmt(meth.getDeclaringClass(), meth,
					rop.getRegister(), vt, to.getType(), stmt.getID(),
					numToAssign, isInSCC);

			absHeap.markChanged(flag);
		}

		// no-op.
		public void visitNullCheck(Quad stmt) {

		}

		// we translate phinode into a set of assignments.
		// PHI node: PHI T5, (T3, T4), { BB3, BB4 }
		public void visitPhi(Quad stmt) {
			jq_Method meth = stmt.getMethod();

			assert stmt.getOperator() instanceof Phi : "Not Phi";

			boolean tmp = false; // just for dbg
			if (Phi.getDest(stmt) instanceof RegisterOperand) {
				boolean sig = false;
				RegisterOperand lhs = Phi.getDest(stmt);
				VariableType lvt = getVarType(meth, lhs.getRegister());

				for (RegisterOperand rhs : stmt.getOperator().getUsedRegisters(
						stmt)) {
					// PHI T5, (null, T4), { BB3, BB4 }
					if (rhs == null)
						continue;

					tmp = true;
					VariableType rvt = getVarType(meth, rhs.getRegister());
					boolean flag = false;
					flag = absHeap.handleAssignStmt(meth.getDeclaringClass(),
							meth, lhs.getRegister(), lvt, rhs.getRegister(),
							rvt, numToAssign, isInSCC);
					sig = flag | sig;
				}

				absHeap.markChanged(sig);
			}

			if (!tmp) {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}

		}

		// v1.f = v2
		public void visitPutfield(Quad stmt) {
			FieldOperand field = Putfield.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference) {
				assert (stmt.getOperator() instanceof Putfield);
				jq_Method meth = stmt.getMethod();
				boolean flag = false;
				Operand rhso = Putfield.getSrc(stmt);
				if (rhso instanceof RegisterOperand) {
					RegisterOperand rhs = (RegisterOperand) rhso;
					RegisterOperand lhs = (RegisterOperand) Putfield
							.getBase(stmt);
					VariableType lvt = getVarType(stmt.getMethod(),
							lhs.getRegister());
					VariableType rvt = getVarType(stmt.getMethod(),
							rhs.getRegister());

					flag = absHeap.handleStoreStmt(meth.getDeclaringClass(),
							meth, lhs.getRegister(), lvt, field.getField(),
							rhs.getRegister(), rvt, numToAssign, isInSCC);

					absHeap.markChanged(flag);
				}
			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}
		}

		// A.f = b;
		public void visitPutstatic(Quad stmt) {
			FieldOperand field = Putstatic.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference) {
				jq_Method meth = stmt.getMethod();
				Operand rhso = Putstatic.getSrc(stmt);
				jq_Class encloseClass = field.getField().getDeclaringClass();
				boolean flag = false;

				if (rhso instanceof RegisterOperand) {
					RegisterOperand rhs = (RegisterOperand) rhso;
					VariableType rvt = getVarType(stmt.getMethod(),
							rhs.getRegister());

					flag = absHeap.handleStaticStoreStmt(
							meth.getDeclaringClass(), meth, encloseClass,
							field.getField(), rhs.getRegister(), rvt,
							numToAssign, isInSCC);

					absHeap.markChanged(flag);
				}
			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}

		}

		public void visitReturn(Quad stmt) {
			boolean flag = false;
			if (stmt.getOperator() instanceof RETURN_A) {
				Operand operand = Return.getSrc(stmt);
				if (!(operand instanceof RegisterOperand))
					return;
				Register ret = ((RegisterOperand) operand).getRegister();
				jq_Method meth = stmt.getMethod();
				jq_Class clazz = meth.getDeclaringClass();
				VariableType type = getVarType(meth, ret);

				flag = absHeap.handleRetStmt(clazz, meth, ret, type,
						numToAssign, isInSCC);

				absHeap.markChanged(flag);

				// if (retValue == null) {
				assert (absHeap.contains(new RetElem(clazz, meth))) : ""
						+ "the return value should be contained in the heap!";
				retValue = absHeap.getRetElem(clazz, meth);
				// }
			} else {
				if (G.debug4Sum) {
					System.out
							.println("[Debug4Sum] Not a processable instruction!");
				}
			}
		}

		// no sure whether we should mark this as no op.
		public void visitSpecial(Quad stmt) {
			System.out.println(stmt);
			assert false : "Special stmt that we havn't consider. Abort.";
		}

		// no-op.
		public void visitStoreCheck(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

		// no sure whether we should mark this as no op.
		public void visitUnary(Quad stmt) {
		}

		// no-op.
		public void visitZeroCheck(Quad stmt) {
			if (G.debug4Sum) {
				System.out
						.println("[Debug4Sum] Not a processable instruction!");
			}
		}

	};

	public int getCurrNumCounter() {
		return currNumCounter;
	}

	// given a call site in the caller, return all the possible callee's
	// summaries and the corresponding constraints as a list of pairs
	// if no callee available, return ret (size == 0)
	public List<Pair<Summary, BoolExpr>> getSumCstPairList(Quad callsite) {
		jq_Method caller = callsite.getMethod();
		jq_Class clz = caller.getDeclaringClass();

		List<Pair<Summary, BoolExpr>> ret = new ArrayList<Pair<Summary, BoolExpr>>();
		// find all qualified callees and the constraints

		jq_Method callee = Invoke.getMethod(callsite).getMethod();

		if (G.tuning)
			StringUtil.reportInfo("static callee: " + callee);

		Operator opr = callsite.getOperator();
		Summary calleeSum = SummariesEnv.v().getSummary(callee);
		// FIXME: if the summary is null, return nothing.
		// But for interface, we need to continue.
		if (calleeSum == null && !(opr instanceof InvokeInterface))
			return ret;
		BoolExpr cst = ConstraintManager.genTrue();

		// trivial cases: final, private, static. We know its exactly target.
		if (opr instanceof InvokeStatic) {
			// always true.
			// invoke_v : v.foo()
			if (opr instanceof INVOKESTATIC_V) {
				ret.add(new Pair<Summary, BoolExpr>(calleeSum, cst));
				// invoke_a : u = v.foo()
			} else if (opr instanceof INVOKESTATIC_A) {
				ret.add(new Pair<Summary, BoolExpr>(calleeSum, cst));
				// handle the return value.
			} else {
				// ignore the rest of cases.
			}
		} else if (opr instanceof InvokeVirtual) {
			// assume all csts are true.
			RegisterOperand ro = Invoke.getParam(callsite, 0);
			Register recv = ro.getRegister();

			assert recv.getType() instanceof jq_Class : "Receiver must be a ref type.";
			// receiver's static type.
			jq_Class recvStatType = (jq_Class) ro.getType();

			// generate pt-set for the receiver.
			StackObject so = getMemLocation(clz, caller, recv);
			P2Set p2Set = absHeap.lookup(so,
					EpsilonFieldElem.getEpsilonFieldElem());

			// assert !p2Set.isEmpty() : "Receiver's p2Set can't be empty.";
			// FIXME: We should assume that v can point to any object.
			if (p2Set.isEmpty()) {
				System.err
						.println("[WARNING:]Receiver's p2Set can't be empty. Missing models?");
				return ret;
			}

			// all dynamic targets.
			Set<Pair<jq_Reference, jq_Method>> tgtSet = SummariesEnv.v()
					.loadInheritMeths(callee, null);

			if (G.tuning)
				StringUtil.reportInfo("resolve callee: " + tgtSet);

			for (Pair<jq_Reference, jq_Method> pair : tgtSet) {
				// generate constraint for each potential target.
				if (pair.val0 instanceof jq_Array)
					continue;
				jq_Class tgtType = (jq_Class) pair.val0;
				if (!tgtType.extendsClass(recvStatType))
					continue;

				assert tgtType.extendsClass(recvStatType) : "Dynamic type must be a subclass for static type!";

				if (SummariesEnv.v().cheating()) {
					if ((callee.getName().toString().equals("study") || callee.getName().toString().equals("match"))
							&& (tgtSet.size() > 30)
							&& (callsite.getMethod().getNameAndDesc()
									.equals(pair.val1.getNameAndDesc()))) {
						if (!tgtType.equals(callsite.getMethod()
								.getDeclaringClass()))
							continue;
					}
				}

				// assert !(callee.getName().toString().equals("study")
				// && (tgtSet.size() > 30) && (callsite.getMethod()
				// .getNameAndDesc().equals(pair.val1.getNameAndDesc()))) :
				// "stop.";

				long startGenCst = System.nanoTime();

				cst = genCst(p2Set, pair.val1, tgtType);
				try {
					cst = (BoolExpr)cst.Simplify();
				} catch (Z3Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (G.tuning) {
					long endGenCst = System.nanoTime();
					G.genCstTime += (endGenCst - startGenCst);
				}
				assert cst != null : "Invalid constaint!";
				Summary dySum = SummariesEnv.v().getSummary(pair.val1);
				if (dySum == null) {
					System.err
							.println("[WARNING:]Unreachable method because of missing model."
									+ pair.val1);
					continue;
				}
				if (G.tuning)
					StringUtil.reportInfo("Generate Constraint: " + cst);
				ret.add(new Pair<Summary, BoolExpr>(dySum, cst));
			}
			if (G.tuning)
				StringUtil.reportInfo("filter callee: " + tgtSet.size()
						+ " -- " + ret.size());

		} else if (opr instanceof InvokeInterface) {
			// assume all csts are true.
			RegisterOperand ro = Invoke.getParam(callsite, 0);
			Register recv = ro.getRegister();
			assert recv.getType() instanceof jq_Class : "Receiver must be a ref type.";
			// generate pt-set for the receiver.
			StackObject so = getMemLocation(clz, caller, recv);
			P2Set p2Set = absHeap.lookup(so,
					EpsilonFieldElem.getEpsilonFieldElem());

			// assert !p2Set.isEmpty() : "Receiver's p2Set can't be empty." ;
			if (p2Set.isEmpty()) {
				if (G.debug4Sum) {
					System.err
							.println("[WARNING:] Receiver's p2Set can't be empty. Missing models?");
				}
				return ret;
			}

			// all dynamic targets.
			Set<Pair<jq_Reference, jq_Method>> tgtSet = SummariesEnv.v()
					.loadInheritMeths(callee, null);

			for (Pair<jq_Reference, jq_Method> pair : tgtSet) {
				if (pair.val0 instanceof jq_Array)
					continue;
				// generate constraint for each potential target.
				cst = genCst(p2Set, pair.val1, (jq_Class) pair.val0);
				assert cst != null : "Invalid constaint!";
				Summary dySum = SummariesEnv.v().getSummary(pair.val1);
				if (dySum == null) {
					if (G.debug4Sum) {
						System.err
								.println("Unreachable method because of missing model."
										+ pair.val1);
					}
					continue;
				}
				ret.add(new Pair<Summary, BoolExpr>(dySum, cst));
			}
			// TODO
		} else {
			assert false : "Unhandled invoke!" + callsite;
		}
		return ret;
	}

	// is this a param or local. helper function.
	public VariableType getVarType(jq_Method meth, Register r) {
		VariableType vt = VariableType.LOCAL_VARIABLE;

		ControlFlowGraph cfg = meth.getCFG();
		RegisterFactory rf = cfg.getRegisterFactory();
		int numArgs = meth.getParamTypes().length;
		for (int zIdx = 0; zIdx < numArgs; zIdx++) {
			Register v = rf.get(zIdx);
			if (v.equals(r)) {
				vt = VariableType.PARAMEMTER;
				break;
			}
		}
		return vt;
	}

	public StackObject getMemLocation(jq_Class clz, jq_Method meth, Register r) {
		VariableType vt = getVarType(meth, r);
		if (vt == VariableType.LOCAL_VARIABLE) {
			return absHeap.getLocalVarElem(clz, meth, r);
		} else if (vt == VariableType.PARAMEMTER) {
			return absHeap.getParamElem(clz, meth, r);
		}
		return null;
	}

	/**
	 * Given a specific method and access path o, return its constraint.
	 * 
	 * @return
	 */
	public BoolExpr genCst(P2Set p2Set, jq_Method callee, jq_Class statT) {
        if(SummariesEnv.v().disableCst())
			return ConstraintManager.genTrue();
		// 1. Base case: No subtype of T override m: type(o) <= T
		if (!hasInherit(callee, statT)) {
			return ConstraintManager.genSubTyping(p2Set, statT);
		} else {
			// 2. Inductive case: for each its *direct* subclasses that
			// do not override current method, call genCst recursively.
			BoolExpr t = ConstraintManager.genFalse();
			for (jq_Class sub : Env.getSuccessors(statT)) {
				if (sub.getVirtualMethod(callee.getNameAndDesc()) != null
						|| hasInherit(callee, sub))
					continue;
				assert !sub.equals(statT) : "do not repeat!";
				BoolExpr phi = genCst(p2Set, callee, sub);
				t = ConstraintManager.union(t, phi);
			}
			// do the union.
			return ConstraintManager.union(t,
					ConstraintManager.genEqTyping(p2Set, statT));
		}
	}

	/**
	 * Check whether given method is override by any of its subclasses.
	 * 
	 * @param callee
	 * @param statT
	 * @param tgt
	 * @return
	 */
	protected boolean hasInherit(jq_Method callee, jq_Class statT) {
		return SummariesEnv.v().loadInheritMeths(callee, statT).size() > 0;
	}

	public AbstractHeap getAbsHeap() {
		return absHeap;
	}

	// perform GC on abstractHeap.
	public void gcAbsHeap() {
		absHeap = null;
	}

	public boolean hasRet() {
		return absHeap.hasRet();
	}

	public void printCalleeHeapInfo(AbstractHeap absHeap) {
		int param2Alloc = 0;
		int param2AP = 0;
		int static2Alloc = 0; // can avoid
		int static2AP = 0;
		int local2Alloc = 0; // can avoid
		int local2AP = 0;
		int total = 0;
		for (Pair<AbstractMemLoc, FieldElem> pair : absHeap.heapObjectsToP2Set
				.keySet()) {
			AbstractMemLoc src = pair.val0;
			FieldElem f = pair.val1;
			P2Set tgts = absHeap.heapObjectsToP2Set.get(pair);
			for (HeapObject tgt : tgts.getHeapObjects()) {
				if (src instanceof ParamElem && tgt instanceof AllocElem) {
					param2Alloc++;
				} else if (src instanceof ParamElem
						&& tgt instanceof AccessPath) {
					param2AP++;
				}
			}
		}
	}
}
