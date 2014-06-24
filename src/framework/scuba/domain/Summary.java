package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
import joeq.Compiler.Quad.Operator.ALoad.ALOAD_A;
import joeq.Compiler.Quad.Operator.AStore;
import joeq.Compiler.Quad.Operator.CheckCast;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEINTERFACE_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKESTATIC_A;
import joeq.Compiler.Quad.Operator.Invoke.INVOKEVIRTUAL_A;
import joeq.Compiler.Quad.Operator.Invoke.InvokeInterface;
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
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.program.Program;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.BoolExpr;

import framework.scuba.analyses.alias.SummaryBasedAnalysis;
import framework.scuba.analyses.dataflow.IntraProcSumAnalysis;
import framework.scuba.domain.AbstractHeap.VariableType;
import framework.scuba.helper.AccessPathHelper;
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
	final protected MemLocInstn4Method memLocInstnResult;

	// used for efficient caching
	final protected MemLocInstnCacheDepMap locDepMap;

	// smart skip for instantiating the callees
	protected Set<MemLocInstnItem> smartSkip = new HashSet<MemLocInstnItem>();

	// finish current summary.
	private boolean terminated;

	// parameter list used for instantiating
	// once initialized, never changed
	protected List<ParamElem> formals;

	// return value list
	protected RetElem retValue;

	// heap for the whole summary has changed?
	protected Pair<Boolean, Boolean> changed = new Pair<Boolean, Boolean>(
			false, false);

	// used for dealing with recursive call
	protected boolean hasAnalyzed = false;

	// alias query in this method or instantiated in this method
	protected AliasQueries aliasQueries;

	// whether current method is in a bad scc.
	protected boolean inBadScc = false;

	public boolean isInBadScc() {
		return inBadScc;
	}

	public void setInBadScc(boolean inBadScc) {
		this.inBadScc = inBadScc;
	}

	public boolean heapIsChanged() {
		return changed.val0;
	}

	public boolean sumIsChanged() {
		return changed.val1;
	}

	public Pair<Boolean, Boolean> isChanged() {
		return changed;
	}

	public void setChanged(Pair<Boolean, Boolean> isChanged) {
		this.changed.val0 = isChanged.val0;
		this.changed.val1 = isChanged.val1;
	}

	public Summary(jq_Method meth) {
		this.method = meth;
		this.absHeap = new AbstractHeap(this);
		this.memLocInstnResult = new MemLocInstn4Method(this);
		this.locDepMap = new MemLocInstnCacheDepMap(this);
		this.aliasQueries = new AliasQueries(meth, this);
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
		// absHeap.dumpHeapMappingToFile(count);
	}

	public void dumpSummaryMappingToFile(String count) {
		// absHeap.dumpHeapToFile(count);
		absHeap.dumpHeapMappingToFile(count);
	}

	public void dumpAllMemLocsHeapToFile(String count) {
		absHeap.dumpAllMemLocsHeapToFile(count);
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

	public jq_Method getMethod() {
		return method;
	}

	public Pair<Boolean, Boolean> handleStmt(Quad quad) {

		if (G.dbgPermission) {
			StringUtil.reportInfo("dbgPermission: " + "handling stmt: " + quad);

		}
		absHeap.markChanged(new Pair<Boolean, Boolean>(false, false));
		quad.accept(qv);
		return absHeap.isChanged();
	}

	public static int tmp = 0;
	public static int tmp1 = 0;
	public static int tmp2 = 0;
	public static int perCallerId = 0;
	public static int perCalleeId = 0;

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
			if (G.dbgSmashing) {
				System.out.println("dbgSmashing: " + " exec stmt: " + stmt);
			}

			Summary.aloadCnt++;

			// only handle ALOAD_A only.
			if (!(stmt.getOperator() instanceof ALOAD_A))
				return;

			jq_Method meth = stmt.getMethod();
			if (ALoad.getDest(stmt) instanceof RegisterOperand) {
				RegisterOperand rhs = (RegisterOperand) ALoad.getBase(stmt);
				RegisterOperand lhs = (RegisterOperand) ALoad.getDest(stmt);

				jq_Class clz = (jq_Class) Program.g().getClass(
						"java.lang.String");
				if (lhs.getType().equals(clz) && SummariesEnv.v().ignoreString)
					return;

				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
						false);

				flag = absHeap.handleALoadStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhs.getRegister(), rvt);

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

				jq_Class clz = (jq_Class) Program.g().getClass(
						"java.lang.String");
				if (rhs.getType().equals(clz) && SummariesEnv.v().ignoreString)
					return;

				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
						false);

				flag = absHeap.handleAStoreStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhs.getRegister(), rvt);

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

		// treat it as assignment.
		public void visitCheckCast(Quad stmt) {
			Summary.castCnt++;

			Operand rx = CheckCast.getSrc(stmt);
			if (rx instanceof RegisterOperand) {
				jq_Method meth = stmt.getMethod();
				RegisterOperand ro = (RegisterOperand) rx;
				if (ro.getType().isReferenceType()) {
					Register r = ro.getRegister();
					RegisterOperand lo = CheckCast.getDest(stmt);
					Register l = lo.getRegister();

					VariableType lvt = getVarType(stmt.getMethod(), l);
					VariableType rvt = getVarType(stmt.getMethod(), r);
					Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
							false, false);
					flag = absHeap.handleAssignStmt(meth.getDeclaringClass(),
							meth, l, lvt, r, rvt);
					absHeap.markChanged(flag);
				}
			}

		}

		// v1 = v2.f
		public void visitGetfield(Quad stmt) {
			// TODO
			FieldOperand field = Getfield.getField(stmt);

			if (field.getField().getType() instanceof jq_Reference) {
				assert (stmt.getOperator() instanceof Getfield);
				RegisterOperand lhs = Getfield.getDest(stmt);

				jq_Class clz = (jq_Class) Program.g().getClass(
						"java.lang.String");
				if (lhs.getType().equals(clz) && SummariesEnv.v().ignoreString)
					return;

				RegisterOperand rhsBase = (RegisterOperand) Getfield
						.getBase(stmt);
				jq_Method meth = stmt.getMethod();
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhsBase.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
						false);
				flag = absHeap.handleLoadStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhsBase.getRegister(),
						field.getField(), rvt);

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

				jq_Class clz = (jq_Class) Program.g().getClass(
						"java.lang.String");
				if (lhs.getType().equals(clz) && SummariesEnv.v().ignoreString)
					return;

				jq_Class encloseClass = field.getField().getDeclaringClass();
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
						false);
				flag = absHeap.handleStatLoadStmt(meth.getDeclaringClass(),
						meth, lhs.getRegister(), lvt, encloseClass,
						field.getField());

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

			StringUtil.reportInfo("Handle invoke----- " + stmt);
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

			if (G.dbgPermission) {
				StringUtil.reportInfo("dbgPermission: "
						+ "========================================");
				StringUtil.reportInfo("dbgPermission: " + "call site: " + stmt);
				StringUtil.reportInfo("dbgPermission: "
						+ "resolved number of callees: "
						+ calleeSumCstPairs.size());
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
				if (G.dbgPermission) {
					StringUtil
							.reportInfo("dbgPermission: "
									+ "------------------------------------------------");
					StringUtil.reportInfo("dbgPermission: " + " this is the "
							+ count + "-th callee" + " out of "
							+ calleeSumCstPairs.size());
					int num = 0;
					for (Pair p : absHeap.locToP2Set.keySet()) {
						num += absHeap.locToP2Set.get(p).size();
					}
					StringUtil.reportInfo("dbgPermission: "
							+ " edges in the current caller: " + num);
					StringUtil.reportInfo("dbgPermission: "
							+ " caller method: " + getMethod() + " ["
							+ perCallerId + " ]");
					StringUtil
							.reportInfo("dbgPermission: "
									+ "~~~~~~~~~~~~~~~~caller sum info~~~~~~~~~~~~~~~~~~~~");
					printCalleeHeapInfo("dbgPermission");
					num = 0;
					for (Pair p : calleeSum.absHeap.locToP2Set.keySet()) {
						num += calleeSum.absHeap.locToP2Set.get(p).size();
					}
					StringUtil.reportInfo("dbgPermission: "
							+ " edges in the current callee: " + num);
					StringUtil.reportInfo("dbgPermission: "
							+ " callee method: " + calleeSum.getMethod() + " ["
							+ perCalleeId + " ]");
					StringUtil
							.reportInfo("dbgPermission: "
									+ "~~~~~~~~~~~~~~~~callee sum info~~~~~~~~~~~~~~~~~~~~");
					calleeSum.printCalleeHeapInfo("dbgPermission");
				}

				// we should only get non-null summary for getSumCstPairList
				assert (calleeSum != null) : "null summary!";
				// we should only get summaries of methods that have been
				// analyzed which is guaranteed by getSumCstPairList
				assert (calleeSum.getFormals() != null) : "null formals list!";
				assert (hasTypeCst != null) : "invalid has type constraint!";

				// get the memory location instantiation for the callee
				MemLocInstnItem item = memLocInstnResult
						.get(new Pair<Quad, jq_Method>(stmt, calleeSum
								.getMethod()));
				if (G.dbgRet) {
					StringUtil.reportInfo(" get the mem loc instn");
					if (item == null) {
						StringUtil.reportInfo("it is a null!");
					} else {
						StringUtil.reportInfo("------");
						item.print();
					}
				}
				// if has not been cached
				if (item == null) {
					item = new MemLocInstnItem(meth, stmt,
							calleeSum.getMethod(), memLocInstnResult);
					memLocInstnResult.put(new Pair<Quad, jq_Method>(stmt,
							calleeSum.getMethod()), item);

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

					item.initFormalToActualMapping(calleeSum.getFormals(),
							actualsMemLoc);
					// fill the return-value mapping
					// ONLY for x = v.foo(a1, a2)
					Operator opr = stmt.getOperator();
					if (opr instanceof INVOKESTATIC_A
							|| opr instanceof INVOKEVIRTUAL_A
							|| opr instanceof INVOKEINTERFACE_A
							|| Invoke.getDest(stmt) != null) {

						if (G.dbgRet) {
							StringUtil.reportInfo("init the return mapping");
						}

						RegisterOperand ro = Invoke.getDest(stmt);
						StackObject sObj = getMemLocation(
								meth.getDeclaringClass(), meth,
								ro.getRegister());
						assert (sObj != null) : "Fails to locate the right heap obj.";
						item.initReturnToLHS(calleeSum.getRetValue(), sObj);
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

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
						false);

				// using smart skip for callee instantiation
				if (SummariesEnv.v().smartSkip) {
					if (smartSkip.contains(item)) {
						continue;
					}
				}

				if (G.instnInfo) {
					StringUtil.reportInfo("instnInfo: " + " there are ["
							+ calleeSum.getAbsHeap().size()
							+ "] edges in the callee heap" + " of method "
							+ calleeSum.getMethod());
				}

				if (G.dbgQuery) {
					StringUtil.reportInfo("dbgQuery: "
							+ "before entering handle invoke");
				}
				flag = absHeap.handleInvokeStmt(meth.getDeclaringClass(), meth,
						stmt.getID(), calleeSum.getAbsHeap(), item, hasTypeCst);

				if (SummariesEnv.v().smartSkip) {
					smartSkip.add(item);
				}

				absHeap.markChanged(flag);
				if (G.dbgPermission) {
					int num = 0;
					for (Pair p : absHeap.locToP2Set.keySet()) {
						num += absHeap.locToP2Set.get(p).size();
					}
					StringUtil.reportInfo("dbgPermission: "
							+ " edges in the current caller: " + num);
					StringUtil.reportInfo("dbgPermission: "
							+ " caller method: " + getMethod() + " ["
							+ perCallerId + " ]");
					StringUtil.reportInfo("dbgPermission: "
							+ "----------------------------------------");
					StringUtil
							.reportInfo("dbgPermission: "
									+ "~~~~~~~~~~~~~~~~caller sum info~~~~~~~~~~~~~~~~~~~~");
					printCalleeHeapInfo("dbgPermission");
				}

				if (G.dbgBlowup
						&& meth.toString()
								.contains(
										"equals:(Ljava/lang/Object;)Z@java.util.Hashtable$Entry")) {
					int num = 0;
					for (Pair p : getAbsHeap().keySet()) {
						num += getAbsHeap().get(p).size();
					}
					StringUtil
							.reportInfo("Current heap size after instantiate: "
									+ num);
				}
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

			if (G.dbgBlowup
					&& meth.toString()
							.contains(
									"equals:(Ljava/lang/Object;)Z@java.util.Hashtable$Entry")) {
				int num = 0;
				for (Pair p : getAbsHeap().keySet()) {
					num += getAbsHeap().get(p).size();
				}
				StringUtil.reportInfo("Current heap size after invoke: " + num);

				/*
				 * if(num < 20) { absHeap.dumpHeapToFile("equals");
				 * System.out.println(meth.getCFG().fullDump());
				 * 
				 * assert false; }
				 */
			}

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

				jq_Class clz = (jq_Class) Program.g().getClass(
						"java.lang.String");
				if (rhs.getType().equals(clz) && SummariesEnv.v().ignoreString)
					return;

				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
						false);
				flag = absHeap.handleAssignStmt(meth.getDeclaringClass(), meth,
						lhs.getRegister(), lvt, rhs.getRegister(), rvt);

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

			jq_Class exception = (jq_Class) Program.g().getClass(
					"java.lang.Exception");

			jq_Method meth = stmt.getMethod();
			TypeOperand to = New.getType(stmt);

			if (to.getType() instanceof jq_Class) {
				jq_Class clz = (jq_Class) to.getType();
				// do not handle new exception.
				if (clz.extendsClass(exception))
					return;
			}

			RegisterOperand rop = New.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = absHeap.handleNewStmt(stmt.getMethod().getDeclaringClass(),
					meth, rop.getRegister(), vt, to.getType(), stmt,
					stmt.getID());

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

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = absHeap.handleMultiNewArrayStmt(meth.getDeclaringClass(),
					meth, rop.getRegister(), vt, to.getType(), plo.length(),
					stmt, stmt.getID());

			absHeap.markChanged(flag);
		}

		// v = new Node[10];
		public void visitNewArray(Quad stmt) {
			Summary.aNewArrayCnt++;
			assert (stmt.getOperator() instanceof NewArray);
			jq_Method meth = stmt.getMethod();
			TypeOperand to = NewArray.getType(stmt);

			jq_Array clz = (jq_Array) Program.g()
					.getClass("java.lang.String[]");
			if (to.getType().equals(clz) && SummariesEnv.v().ignoreString)
				return;

			RegisterOperand rop = NewArray.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = absHeap.handleNewArrayStmt(meth.getDeclaringClass(), meth,
					rop.getRegister(), vt, to.getType(), stmt, stmt.getID());

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
				Pair<Boolean, Boolean> sig = new Pair<Boolean, Boolean>(false,
						false);
				RegisterOperand lhs = Phi.getDest(stmt);
				VariableType lvt = getVarType(meth, lhs.getRegister());

				for (RegisterOperand rhs : stmt.getOperator().getUsedRegisters(
						stmt)) {
					// PHI T5, (null, T4), { BB3, BB4 }
					if (rhs == null)
						continue;

					tmp = true;
					VariableType rvt = getVarType(meth, rhs.getRegister());
					Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
							false, false);
					flag = absHeap.handleAssignStmt(meth.getDeclaringClass(),
							meth, lhs.getRegister(), lvt, rhs.getRegister(),
							rvt);
					sig.val0 = flag.val0 | sig.val0;
					sig.val1 = flag.val1 | sig.val1;
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

				Operand rhso = Putfield.getSrc(stmt);
				if (rhso instanceof RegisterOperand) {
					RegisterOperand rhs = (RegisterOperand) rhso;
					RegisterOperand lhs = (RegisterOperand) Putfield
							.getBase(stmt);

					jq_Class clz = (jq_Class) Program.g().getClass(
							"java.lang.String");
					if (rhs.getType().equals(clz)
							&& SummariesEnv.v().ignoreString)
						return;

					VariableType lvt = getVarType(stmt.getMethod(),
							lhs.getRegister());
					VariableType rvt = getVarType(stmt.getMethod(),
							rhs.getRegister());

					Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
							false, false);

					flag = absHeap.handleStoreStmt(meth.getDeclaringClass(),
							meth, lhs.getRegister(), lvt, field.getField(),
							rhs.getRegister(), rvt);

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

				if (rhso instanceof RegisterOperand) {
					RegisterOperand rhs = (RegisterOperand) rhso;

					jq_Class clz = (jq_Class) Program.g().getClass(
							"java.lang.String");
					if (rhs.getType().equals(clz)
							&& SummariesEnv.v().ignoreString)
						return;

					VariableType rvt = getVarType(stmt.getMethod(),
							rhs.getRegister());

					Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(
							false, false);
					flag = absHeap.handleStaticStoreStmt(
							meth.getDeclaringClass(), meth, encloseClass,
							field.getField(), rhs.getRegister(), rvt);
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
			// if (stmt.getOperator() instanceof RETURN_A) {
			Operand operand = Return.getSrc(stmt);
			if (!(operand instanceof RegisterOperand))
				return;
			Register ret = ((RegisterOperand) operand).getRegister();
			jq_Method meth = stmt.getMethod();
			jq_Class clazz = meth.getDeclaringClass();
			VariableType type = getVarType(meth, ret);

			Pair<Boolean, Boolean> flag = new Pair<Boolean, Boolean>(false,
					false);
			flag = absHeap.handleRetStmt(clazz, meth, ret, type);
			absHeap.markChanged(flag);

			// if (retValue == null) {
			assert (absHeap.contains(new RetElem(clazz, meth))) : ""
					+ "the return value should be contained in the heap!";
			retValue = absHeap.getRetElem(clazz, meth);
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

	// given a call site in the caller, return all the possible callee's
	// summaries and the corresponding constraints as a list of pairs
	// if no callee available, return ret (size == 0)
	public List<Pair<Summary, BoolExpr>> getSumCstPairList(Quad callsite) {
		jq_Method caller = callsite.getMethod();
		jq_Class clz = caller.getDeclaringClass();
		BoolExpr cst = ConstraintManager.genTrue();
		Operator opr = callsite.getOperator();
		List<Pair<Summary, BoolExpr>> ret = new ArrayList<Pair<Summary, BoolExpr>>();
		// find all qualified callees and the constraints
		// all dynamic targets.
		Set<jq_Method> tgtSet = Env.cg.getTargets(callsite);
		if (tgtSet.size() == 0) {
			StringUtil
					.reportInfo("Fail to find model for callee..." + callsite);
			return ret;
		}

		// include stativinvoke, vitualinvoke with one target.
		if (tgtSet.size() == 1) {
			jq_Method callee = tgtSet.iterator().next();
			Summary calleeSum = SummariesEnv.v().getSummary(callee);

			if (SummariesEnv.v().cheating()) {
				String signature = callee.toString();
				if (SummariesEnv.v().isStubMethod(signature))
					return ret;
			}

			if (calleeSum == null) {
				StringUtil.reportInfo("Missing model for " + callee);
				return ret;
			}
			// assert calleeSum != null : "CalleeSum can not be null" +
			// callsite;
			if (calleeSum.hasAnalyzed()) {
				ret.add(new Pair<Summary, BoolExpr>(calleeSum, cst));
				return ret;
			}
		}

		if ((opr instanceof InvokeVirtual) || (opr instanceof InvokeInterface)) {
			RegisterOperand ro = Invoke.getParam(callsite, 0);

			if (ro.getType() instanceof jq_Array) {
				StringUtil.reportInfo("Do not handle array for " + callsite);
				return ret;
			}
			Register recv = ro.getRegister();

			assert recv.getType() instanceof jq_Class : "Receiver must be a ref type.";
			// receiver's static type.
			jq_Class recvStatType = (jq_Class) ro.getType();

			// generate pt-set for the receiver.
			StackObject so = getMemLocation(clz, caller, recv);
			P2Set p2Set = absHeap.lookup(so,
					EpsilonFieldElem.getEpsilonFieldElem());

			// only one target, resolve it as static call.
			for (jq_Method tgt : tgtSet) {
				// generate constraint for each potential target.
				jq_Class tgtType = tgt.getDeclaringClass();
				// this is unsound!
				if (SummariesEnv.v().cheating()) {
					String signature = tgt.toString();
					if (SummariesEnv.v().isStubMethod(signature))
						continue;
				}

				if (SummariesEnv.v().disableCst || inBadScc)
					cst = ConstraintManager.genTrue();
				else
					cst = genCst(p2Set, tgt, tgtType, tgtSet);

				// FIXME: We should assume that v can point to any object.
				if (p2Set.isEmpty()) {
					System.err
							.println("[WARNING:]Receiver's p2Set can't be empty."
									+ " Missing models? append True cst."
									+ callsite);
					cst = ConstraintManager.genTrue();
				}

				assert cst != null : "Invalid constaint!";
				Summary dySum = SummariesEnv.v().getSummary(tgt);
				if (dySum == null) {
					System.err
							.println("[WARNING:]Unreachable method because of missing model."
									+ tgt);
					continue;
				}

				if (dySum.hasAnalyzed())
					ret.add(new Pair<Summary, BoolExpr>(dySum, cst));
			}
		} else {
			StringUtil.reportInfo("May be recursive call." + tgtSet);
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
	public BoolExpr genCst(P2Set p2Set, jq_Method callee, jq_Class statT,
			Set<jq_Method> tgtSet) {
		if (SummariesEnv.v().disableCst())
			return ConstraintManager.genTrue();
		// 1. Base case: No subtype of T override m: type(o) <= T
		if (!hasInherit(callee, statT, tgtSet)) {
			return ConstraintManager.genSubTyping(p2Set, statT);
		} else {
			// 2. Inductive case: for each its *direct* subclasses that
			// do not override current method, call genCst recursively.
			BoolExpr t = ConstraintManager.genFalse();
			for (jq_Class sub : Env.getSuccessors(statT)) {
				if (sub.getVirtualMethod(callee.getNameAndDesc()) != null
						|| hasInherit(callee, sub, tgtSet))
					continue;
				assert !sub.equals(statT) : "do not repeat!";
				BoolExpr phi = genCst(p2Set, callee, sub, tgtSet);
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
	protected boolean hasInherit(jq_Method callee, jq_Class statT,
			Set<jq_Method> tgtSet) {

		for (jq_Method tgt : tgtSet) {
			if (tgt.equals(callee))
				continue;
			jq_Class dyClz = tgt.getDeclaringClass();
			if (dyClz.extendsClass(statT))
				return true;
		}
		return false;
	}

	public AbstractHeap getAbsHeap() {
		return absHeap;
	}

	// perform GC on abstractHeap.
	public void gcAbsHeap() {
		absHeap = null;
	}

	public boolean hasAnalyzed() {
		return hasAnalyzed;
	}

	public void setHasAnalyzed() {
		hasAnalyzed = true;
	}

	public void printCalleeHeapInfo(String s) {
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
		StringUtil.reportInfo(s + ": ret --> Alloc: " + ret2Alloc + " out of "
				+ total);
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

	public LocalVarElem getLocalVarElem(jq_Class clazz, jq_Method method,
			Register variable) {
		return absHeap.getLocalVarElem(clazz, method, variable);
	}

	public Set<AllocElem> getP2Set(LocalVarElem local) {
		Set<AllocElem> ret = new HashSet<AllocElem>();
		assert (local != null);
		P2Set p2set = absHeap.locToP2Set.get(new Pair<AbsMemLoc, FieldElem>(
				local, EpsilonFieldElem.getEpsilonFieldElem()));
		if (p2set == null) {
			return ret;
		}
		for (HeapObject hObj : p2set.keySet()) {
			if (hObj instanceof AllocElem) {
				ret.add((AllocElem) hObj);
			} else {
				assert (hObj instanceof StaticAccessPath)
						&& (hObj.findRoot() instanceof StaticElem) : ""
						+ "only StaticElem AccessPath allowed in the entry!";
				AccessPathHelper.resolve(absHeap, (StaticAccessPath) hObj, ret);
			}
		}
		return ret;
	}

	public int getHeapSize() {
		return absHeap.size();
	}

	public Map<MemLocInstnItem, Set<AccessPath>> addToDepMap(AbsMemLoc loc,
			Pair<MemLocInstnItem, Set<AccessPath>> deps) {
		return locDepMap.add(loc, deps);
	}

	public void removeLocals() {
		Iterator<Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set>> it = absHeap.locToP2Set
				.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Pair<AbsMemLoc, FieldElem>, P2Set> entry = it.next();
			Pair<AbsMemLoc, FieldElem> pair = entry.getKey();
			AbsMemLoc loc = pair.val0;
			if (loc instanceof LocalVarElem) {
				absHeap.locToP2Set.remove(pair);
			}
		}
	}

	public boolean filterTgt(jq_Method caller, jq_Method callee) {
		jq_Class callerClz = caller.getDeclaringClass();
		jq_Class calleeClz = callee.getDeclaringClass();
		if (calleeClz.extendsClass(callerClz) && !calleeClz.equals(callerClz)
				&& !callee.isAbstract()) {
			if (calleeClz.getVirtualMethod(caller.getNameAndDesc()) != null) {
				return true;
			}
		}
		return false;
	}
}
