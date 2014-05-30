package framework.scuba.domain;

public class P2SetElem {
	// P2SetElem is a pair in the form: (o, phi)

	// this is the o
	HeapObject heapObject;
	// this is the phi
	Constraint constraint;

	public P2SetElem(HeapObject heapObject, Constraint constraint) {
		this.heapObject = heapObject;
		this.constraint = constraint;
	}

}
