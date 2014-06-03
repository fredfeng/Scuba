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
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
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

	// this maps store the memory location instantiation result for each invoke
	// stmt (call site) in the method that this Summary instance belongs to
	// invoke stmt includes: InvokeVirtual, InvokeStatic, and InvokeInterface
	private Map<Invoke, MemLocInstantiation> virtCallToMemLocInstantiation;

	// finish current summary.
	private boolean terminated;

	public Summary(jq_Method meth) {
		method = meth;
		absHeap = new AbstractHeap();
		virtCallToMemLocInstantiation = new HashMap<Invoke, MemLocInstantiation>();
		if (G.debug)
			this.dumpSummary4Method(meth);
	}

	public void dumpSummary4Method(jq_Method meth) {
		System.out.println("Summary for method: " + meth.getName());
		System.out.println("**************************************");
		Set<jq_Type> allocs = new HashSet();
		List<jq_Type> allocList = new ArrayList();

		Set<Operand> fieldsBase = new HashSet();
		List<Operand> fieldsBaseList = new ArrayList();

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
					fieldsBase.add(Putfield.getBase(q));
					fieldsBaseList.add(Putfield.getBase(q));

					fieldsAccess.add(Putfield.getField(q).getField());
					fieldsAccList.add(Putfield.getField(q).getField());

				}

				if (op instanceof Getfield) {
					fieldsBase.add(Getfield.getBase(q));
					fieldsBaseList.add(Putfield.getBase(q));

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

	public void handleStmt(Quad quad) {
		quad.accept(qv);
	}

	QuadVisitor qv = new QuadVisitor.EmptyVisitor() {

		public void visitALength(Quad stmt) {

		}

		// perform array smashing. Use assign to handle array store/load.
		public void visitALoad(Quad stmt) {
			// TODO
			jq_Method meth = stmt.getMethod();
			if (ALoad.getDest(stmt) instanceof RegisterOperand) {
				RegisterOperand rhs = (RegisterOperand) ALoad.getBase(stmt);
				RegisterOperand lhs = (RegisterOperand) ALoad.getDest(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				boolean flag = absHeap.handleAssignStmt(
						meth.getDeclaringClass(), meth, lhs.getRegister(), lvt,
						rhs.getRegister(), rvt);
				absHeap.markChanged(flag);
			}
		}

		public void visitAStore(Quad stmt) {
			// TODO
			jq_Method meth = stmt.getMethod();
			if (AStore.getValue(stmt) instanceof RegisterOperand) {
				RegisterOperand lhs = (RegisterOperand) AStore.getBase(stmt);
				RegisterOperand rhs = (RegisterOperand) AStore.getValue(stmt);
				VariableType lvt = getVarType(stmt.getMethod(),
						lhs.getRegister());
				VariableType rvt = getVarType(stmt.getMethod(),
						rhs.getRegister());

				boolean flag = absHeap.handleAssignStmt(
						meth.getDeclaringClass(), meth, lhs.getRegister(), lvt,
						rhs.getRegister(), rvt);
				absHeap.markChanged(flag);
			}
		}

		public void visitBinary(Quad stmt) {
		}

		public void visitBoundsCheck(Quad stmt) {
		}

		public void visitBranch(Quad stmt) {
		}

		public void visitCheckCast(Quad stmt) {
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
						field.getField(), rvt);
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
						encloseClass, field.getField());
				absHeap.markChanged(flag);
			}
		}

		public void visitInstanceOf(Quad stmt) {
		}

		public void visitInvoke(Quad stmt) {
			// TODO

		}

		public void visitMemLoad(Quad stmt) {
		}

		public void visitMemStore(Quad stmt) {
		}

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
						rhs.getRegister(), rvt);
				absHeap.markChanged(flag);
			}
		}

		public void visitMultiNewArray(Quad stmt) {
			// TODO
			assert (stmt.getOperator() instanceof MultiNewArray);
			jq_Method meth = stmt.getMethod();
			TypeOperand to = MultiNewArray.getType(stmt);
			RegisterOperand rop = MultiNewArray.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			boolean flag = absHeap.handleNewStmt(stmt.getMethod()
					.getDeclaringClass(), meth, rop.getRegister(), vt, to
					.getType(), stmt.getLineNumber());
			absHeap.markChanged(flag);
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
					.getType(), stmt.getLineNumber());
			absHeap.markChanged(flag);
		}

		// v = new Array(); is it ok if we use the same handler as handlerNew
		// for
		// array?
		public void visitNewArray(Quad stmt) {
			// TODO
			assert (stmt.getOperator() instanceof NewArray);
			jq_Method meth = stmt.getMethod();
			TypeOperand to = NewArray.getType(stmt);
			RegisterOperand rop = NewArray.getDest(stmt);
			VariableType vt = getVarType(meth, rop.getRegister());

			boolean flag = absHeap.handleNewStmt(stmt.getMethod()
					.getDeclaringClass(), meth, rop.getRegister(), vt, to
					.getType(), stmt.getLineNumber());
			absHeap.markChanged(flag);
		}

		public void visitNullCheck(Quad stmt) {
		}

		public void visitPhi(Quad stmt) {
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
							rhs.getRegister(), rvt);
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
							field.getField(), rhs.getRegister(), rvt);
					absHeap.markChanged(flag);
				}
			}

		}

		public void visitReturn(Quad stmt) {
			// TODO
		}

		public void visitSpecial(Quad stmt) {
		}

		public void visitStoreCheck(Quad stmt) {
		}

		public void visitUnary(Quad stmt) {
		}

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
}
