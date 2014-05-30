package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

public abstract class HeapObject extends AbstractMemLoc {

	protected Set<FieldElem> fields = new HashSet<FieldElem>();

	abstract public AbstractMemLoc findRoot();

}
