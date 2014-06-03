package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import joeq.Class.jq_Field;
import joeq.Class.jq_InstanceField;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.FieldOperand;
import joeq.Compiler.Quad.Operator;
import joeq.Compiler.Quad.Operator.Getfield;
import joeq.Compiler.Quad.Operator.Getstatic;
import joeq.Compiler.Quad.Operator.MultiNewArray;
import joeq.Compiler.Quad.Operator.New;
import joeq.Compiler.Quad.Operator.NewArray;
import joeq.Compiler.Quad.Operator.Putfield;
import joeq.Compiler.Quad.Operator.Putstatic;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.RegisterFactory;
import joeq.Compiler.Quad.RegisterFactory.Register;
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

	// finish current summary.
	private boolean terminated;

	public Summary(jq_Method meth) {
		method = meth;
		absHeap = new AbstractHeap();
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

		public void visitALoad(Quad stmt) {
			// TODO
			absHeap.handleALoadStmt(stmt);
		}

		public void visitAStore(Quad stmt) {
			// TODO
			absHeap.handleAStoreStmt(stmt);
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
			if (field.getField().getType() instanceof jq_Reference)
				absHeap.handleGetfieldStmt(stmt);
		}

		public void visitGetstatic(Quad stmt) {
			// TODO
			FieldOperand field = Getstatic.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference)
				absHeap.handleGetstaticStmt(stmt);
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

		public void visitMove(Quad stmt) {
			// TODO
			absHeap.handleMoveStmt(stmt);
		}

		public void visitMultiNewArray(Quad stmt) {
			// TODO
			absHeap.handleMultiNewArrayStmt(stmt);
		}

		public void visitNew(Quad stmt) {
			// TODO
			absHeap.handleNewStmt(stmt);
		}

		public void visitNewArray(Quad stmt) {
			// TODO
			absHeap.handleNewArrayStmt(stmt);
		}

		public void visitNullCheck(Quad stmt) {
		}

		public void visitPhi(Quad stmt) {
		}

		public void visitPutfield(Quad stmt) {
			// TODO
			FieldOperand field = Putfield.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference)
				absHeap.handlePutfieldStmt(stmt);
		}

		public void visitPutstatic(Quad stmt) {
			// TODO
			FieldOperand field = Putstatic.getField(stmt);
			if (field.getField().getType() instanceof jq_Reference)
				absHeap.handlePutstaticStmt(stmt);
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

	};
}
