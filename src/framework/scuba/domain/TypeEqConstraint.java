package framework.scuba.domain;

import joeq.Class.jq_Type;

public class TypeEqConstraint extends Constraint {
	private AccessPath accessElem;
	private jq_Type elemType;

	public TypeEqConstraint(AccessPath ap, jq_Type t) {
		this.accessElem = ap;
		this.elemType = t;
	}
	
	public jq_Type getElemType() {
		return elemType;
	}
	
	@Override
	public String toString() {
		// type(a.e.f) = T?
		return "type(" + accessElem.toString() + ")=" + elemType.getName();
	}
	
	public Constraint clone() {
		return new TypeEqConstraint(accessElem, elemType);
	}
}
