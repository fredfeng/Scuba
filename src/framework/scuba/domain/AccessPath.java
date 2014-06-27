package framework.scuba.domain;

import java.util.HashSet;
import java.util.Set;

public abstract class AccessPath extends HeapObject {

	protected Set<FieldElem> smashed = new HashSet<FieldElem>();

	abstract public AbsMemLoc getBase();

	abstract public FieldElem getField();

	abstract public int getId();

	abstract public StackObject findRoot();

	abstract public AccessPath getPrefix(FieldElem f);

	abstract public AccessPath findPrefix(FieldElem f);

	public boolean isSmashed() {
		return !smashed.isEmpty();
	}

	public void addSmashedField(FieldElem f) {
		assert (f instanceof NormalFieldElem || f instanceof IndexFieldElem) : ""
				+ "only normal field and index field can be smashed!";
		smashed.add(f);
	}

	public void addSmashedFields(Set<FieldElem> fields) {
		for (FieldElem f : fields) {
			assert (f instanceof NormalFieldElem || f instanceof IndexFieldElem) : ""
					+ "only normal field and index field can be smashed!";
		}
		smashed.addAll(fields);
	}

	public Set<FieldElem> getSmashedFields() {
		return smashed;
	}
}
