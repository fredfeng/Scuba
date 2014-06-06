package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import joeq.Compiler.Quad.Operator.Move;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Operator.Return.RETURN_A;
import joeq.Compiler.Quad.Operator.Return.RETURN_V;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.util.tuple.object.Pair;

import com.microsoft.z3.Expr;

import framework.scuba.domain.AbstractHeap.VariableType;
import framework.scuba.helper.G;

/**
 * Representing the summary for a method. Now it only contains abstractHeap.
 * 
 * @author yufeng
 * 
 */
public class Summary {

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
	protected int numberCounter = 0;

	// used for numbering
	protected boolean isInSCC = false;

	// parameter list used for instantiating
	// once initialized, never changed
	protected List<ParamElem> formals;

	// return value list
	protected RetElem retValue;

	public Summary(jq_Method meth) {
		method = meth;
		absHeap = new AbstractHeap();
		methCallToMemLocInstantiation = new HashMap<Pair<Quad, jq_Method>, MemLocInstantiation>();
		if (G.debug)
			this.dumpSummary4Method(meth);
	}

	// initialize the paramList
	// this will be done ONLY once!
	public void initFormals() {
		formals = new ArrayList<ParamElem>();
	}

	// fill the paramList from left to right, one by one, by the location in the
	// heap
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
		Set<jq_Type> allocs = new HashSet();
		List<jq_Type> allocList = new ArrayList();

		Set<Register> fieldsBase = new HashSet();
		List<Register> fieldsBaseList = new ArrayList();

		Set<jq_Field> fieldsAccess = new HashSet();
		List<jq_Field> fieldsAccList = new ArrayList();

		Set<Register> locals = new HashSet();

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

	public void dumpSummaryToFile(int count) {
		if (G.debug)
			absHeap.dumpHeapToFile(count);
	}

	public void dumpAllMemLocsHeapToFile(int count) {
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

	public AbstractHeap getAbstractHeap() {
		return absHeap;
	}

	public jq_Method getMethod() {
		return method;
	}

	public void handleStmt(Quad quad, int numCounter, boolean isInSCC) {
		// I think this is the only to pass the counter and isInSCC to the
		// visitor
		this.numberCounter = numCounter;
		this.isInSCC = isInSCC;
		quad.accept(qv);
	}

	QuadVisitor qv = new QuadVisitor.EmptyVisitor() {

		// no-op.
		public void visitALength(Quad stmt) {

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

				boolean flag = absHeap.handleALoadStmt(
						meth.getDeclaringClass(), meth, lhs.getRegister(), lvt,
						rhs.getRegister(), rvt, numberCounter, isInSCC);
				absHeap.markChanged(flag);
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

				boolean flag = absHeap.handleAStoreStmt(
						meth.getDeclaringClass(), meth, lhs.getRegister(), lvt,
						rhs.getRegister(), rvt, numberCounter, isInSCC);
				absHeap.markChanged(flag);
			}
		}

		// no-op.
		public void visitBinary(Quad stmt) {
		}

		// no-op.
		public void visitBoundsCheck(Quad stmt) {
		}

		// no-op.
		public void visitBranch(Quad stmt) {
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

				boolean flag = absHeap.handleLoadStmt(meth.getDeclaringClass(),
						meth, lhs.getRegister(), lvt, rhsBase.getRegister(),
						field.getField(), rvt, numberCounter, isInSCC);
				absHeap.markChanged(flag);
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

				boolean flag = absHeap.handleStatLoadStmt(
						meth.getDeclaringClass(), meth, lhs.getRegister(), lvt,
						encloseClass, field.getField(), numberCounter, isInSCC);
				absHeap.markChanged(flag);
			}
		}

		// no-op.
		public void visitInstanceOf(Quad stmt) {
		}

		public void visitInvoke(Quad stmt) {
			// TODO
			assert (stmt.getOperator() instanceof Invoke);

			// retrieve the summaries of the potential callees
			List<Pair<Summary, Constraint>> calleeSumCstPairs = getSumCstPairList(stmt);

			// if coming here, it means the callee summary is available

			// the callsite's belonging method
			jq_Method meth = stmt.getMethod();
			// iterate all summaries of all the potential callees
			for (Pair<Summary, Constraint> calleeSumCst : calleeSumCstPairs) {
				// the summary of the callee
				Summary calleeSum = calleeSumCst.val0;
				// the constraint for calling that callee
				Constraint hasTypeCst = calleeSumCst.val1;
				// get the memory location instantiation for the callee
				MemLocInstantiation memLocInstn = methCallToMemLocInstantiation
						.get(new Pair<Quad, jq_Method>(stmt, calleeSum
								.getMethod()));

				// we initialize the instantiation by adding the base cases
				// (param list mapping and return value mapping)
				if (memLocInstn == null) {
					// the first time to do the memory location instantiation,
					// we should init the instantiation by creating the
					// formal-to-actual mapping
					memLocInstn = new MemLocInstantiation(meth, stmt, Invoke
							.getMethod(stmt).getMethod());
					methCallToMemLocInstantiation.put(
							new Pair<Quad, jq_Method>(stmt, calleeSum
									.getMethod()), memLocInstn);
					// fill the formal-to-actual mapping
					ParamListOperand actuals = Invoke.getParamList(stmt);
					List<StackObject> actualsMemLoc = new ArrayList<StackObject>();
					for (int i = 0; i < actuals.length(); i++) {
						Register v = actuals.get(i).getRegister();
						if (getVarType(meth, v) == VariableType.PARAMEMTER) {
							actualsMemLoc.add(absHeap.getLocalVarElem(
									meth.getDeclaringClass(), meth, v));
						} else if (getVarType(meth, actuals.get(i)
								.getRegister()) == VariableType.LOCAL_VARIABLE) {
							actualsMemLoc.add(absHeap.getParamElem(
									meth.getDeclaringClass(), meth, v));
						} else {
							// actuals can be primitives, we use constants to
							// denote those (we do not map formals to constants)
							actualsMemLoc.add(ConstantElem.getConstantElem());
						}
					}
					// fill the formal-to-actual mapping
					memLocInstn.initFormalToActualMapping(
							calleeSum.getFormals(), actualsMemLoc);
					// fill the return-value mapping
					// ONLY for x = v.foo(a1, a2)
					// TODO
					// memLocInstn.initReturnToLHS(calleeSum.getRetValue(),
					// lhs);
				}
				// by now, we have the formal-to-actual mapping as a trigger for
				// the whole instantiation of memory locations and we can start
				// the core instantiation
				// here we go!
				boolean flag = absHeap.handleInvokeStmt(
						meth.getDeclaringClass(), meth, stmt.getID(),
						calleeSum.getAbsHeap(), memLocInstn, hasTypeCst,
						numberCounter, isInSCC);
				absHeap.markChanged(flag);
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
		}

		// v1 = v2
		public void visitMove(Quad stmt) {
			// TODO
			jq_Method meth = stmt.getMethod();
			if (Move.getSrc(stmt) instanceof RegisterOperand) {
				RegisterOperand rhs = (RegisterOperand) Move.getSrc(stmt);
				RegisterOperand lhs = (RegisterOperand) Move.getDest(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());
				boolean flag = absHeap.handleAssignStmt(
						meth.getDeclaringClass(), meth, lhs.getRegister(), lvt,
						rhs.getRegister(), rvt, numberCounter, isInSCC);
				absHeap.markChanged(flag);
			}
		}

		// v1 = new A();
		public void visitNew(Quad stmt) {
			// TODO
			assert (stmt.getOperator() instanceof New);
			jq_Method meth = stmt.getMethod();
			TypeOperand to = New.getType(stmt);
			RegisterOperand rop = New.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			boolean flag = absHeap.handleNewStmt(stmt.getMethod()
					.getDeclaringClass(), meth, rop.getRegister(), vt, to
					.getType(), stmt.getID(), numberCounter, isInSCC);
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
			boolean flag = absHeap.handleMultiNewArrayStmt(
					meth.getDeclaringClass(), meth, rop.getRegister(), vt,
					to.getType(), plo.length(), stmt.getID(), numberCounter,
					isInSCC);
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

			boolean flag = absHeap.handleNewArrayStmt(meth.getDeclaringClass(),
					meth, rop.getRegister(), vt, to.getType(), stmt.getID(),
					numberCounter, isInSCC);
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

			if (Phi.getDest(stmt) instanceof RegisterOperand) {
				boolean sig = false;
				RegisterOperand lhs = Phi.getDest(stmt);
				VariableType lvt = getVarType(meth, lhs.getRegister());

				for (RegisterOperand rhs : stmt.getOperator().getUsedRegisters(
						stmt)) {
					// PHI T5, (null, T4), { BB3, BB4 }
					if (rhs == null)
						continue;

					VariableType rvt = getVarType(meth, rhs.getRegister());
					boolean flag = absHeap
							.handleAssignStmt(meth.getDeclaringClass(), meth,
									lhs.getRegister(), lvt, rhs.getRegister(),
									rvt, numberCounter, isInSCC);
					sig = flag | sig;

				}
				absHeap.markChanged(sig);
			}
		}

		// v1.f = v2
		public void visitPutfield(Quad stmt) {
			// TODO
			FieldOperand field = Putfield.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference) {
				assert (stmt.getOperator() instanceof Putfield);
				jq_Method meth = stmt.getMethod();
				boolean flag;
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
							rhs.getRegister(), rvt, numberCounter, isInSCC);
					absHeap.markChanged(flag);
				}
			}
		}

		// A.f = b;
		public void visitPutstatic(Quad stmt) {
			// TODO
			FieldOperand field = Putstatic.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference) {
				jq_Method meth = stmt.getMethod();
				Operand rhso = Putstatic.getSrc(stmt);
				jq_Class encloseClass = field.getField().getDeclaringClass();
				boolean flag;

				if (rhso instanceof RegisterOperand) {
					RegisterOperand rhs = (RegisterOperand) rhso;
					VariableType rvt = getVarType(stmt.getMethod(),
							rhs.getRegister());

					flag = absHeap.handleStaticStoreStmt(
							meth.getDeclaringClass(), meth, encloseClass,
							field.getField(), rhs.getRegister(), rvt,
							numberCounter, isInSCC);
					absHeap.markChanged(flag);
				}
			}

		}

		public void visitReturn(Quad stmt) {
			// TODO
			// make sure a return stmt can only contains one operand which is
			// Register type, and this Register must be used before returning
			boolean flag = false;
			if (stmt.getOperator() instanceof RETURN_A) {
				List<RegisterOperand> rets = stmt.getUsedRegisters();
				assert (rets.size() == 1) : "we can ONLY return one register!";
				Register ret = rets.get(0).getRegister();
				jq_Method meth = stmt.getMethod();
				jq_Class clazz = meth.getDeclaringClass();
				VariableType type = getVarType(meth, ret);
				flag = absHeap.handleRetStmt(clazz, meth, ret, type,
						numberCounter, isInSCC);

				// TODO
				// just set the retValue of this summary
				// maybe this is not necessary?
				if (retValue == null) {
					assert (absHeap.contains(new RetElem(clazz, meth, ret))) : ""
							+ "the return value should be contained in the heap!";
					retValue = absHeap.getRetElem(clazz, meth, ret);
				}
			} else if (stmt.getOperator() instanceof RETURN_V) {
				// not create RetElem for the return value
			}
		}

		// no sure whether we should mark this as no op.
		public void visitSpecial(Quad stmt) {
			System.out.println(stmt);
			assert false : "special.....";
		}

		// no-op.
		public void visitStoreCheck(Quad stmt) {
		}

		// no sure whether we should mark this as no op.
		public void visitUnary(Quad stmt) {
		}

		// no-op.
		public void visitZeroCheck(Quad stmt) {
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

	};

	public void setNumberCounter(int numberCounter) {
		this.numberCounter = numberCounter;
	}

	public void setIsInSCC(boolean isInSCC) {
		this.isInSCC = isInSCC;
	}

	public int getNumberCounter() {
		return numberCounter;
	}

	// given a call site in the caller, return all the possible callee's
	// summaries and the corresponding constraints as a list of pairs
	// if no callee available, return ret (size == 0)
	public List<Pair<Summary, Constraint>> getSumCstPairList(Quad callsite) {
		// TODO
		List<Pair<Summary, Constraint>> ret = new ArrayList<Pair<Summary, Constraint>>();
		// find all qualified callees and the constraints
		return ret;
	}
	
	//constraint instantiation: return a new instantiated expr.
	public Expr instCst(Expr expr) {
		return null;
	}
	
	/**
	 * lift operation: Given a heap object, return its term.
	 * if ho is an allocElem, return its number;
	 * if ho is an accesspath, return its unique variable v_i
	 * where i is read from a global counter.
	 * @param ho
	 * @return
	 */
	public Expr lift(HeapObject ho) {
		if(ho instanceof AllocElem) {
			//return the number of its class.
		} else if (ho instanceof AccessPath) {
			//return int_constant v_i where i is the id of ap.
		} else {
			assert false : "Unknown heap object.";
		}
		return null;
	}
	
	/**
	 * Given a specific method and access path o, return its constraint.
	 * @return
	 */
	public Expr genCst() {
		//1. Base case: No subtype of T override m: type(o) <= T
		
		//2. Inductive case:
		return null;
	}

	public AbstractHeap getAbsHeap() {
		return absHeap;
	}

	public int getMaxNumber() {
		return absHeap.getMaxNumber();
	}
}
