package framework.scuba.domain;

public class AllocElem extends HeapObject {

	protected Alloc alloc;

	protected Context context;

	public AllocElem(Alloc allocSite, Context context) {
		this.alloc = allocSite;
		this.context = context;
	}

	public AbstractMemLoc findRoot() {
		return this;
	}

	public void appendContextFront(ProgramPoint point) {
		this.context.appendFront(point);
	}

	public void appendContextEnd(ProgramPoint point) {
		this.context.appendEnd(point);
	}

	public Alloc getAlloc() {
		return this.alloc;
	}

	@Override
	public String toString() {
		return alloc + " || " + context;
	}

	@Override
	public boolean equals(Object other) {

		return (other instanceof AllocElem)
				&& (alloc.equals(((AllocElem) other).alloc))
				&& (context.equals(((AllocElem) other).context));
	}

	@Override
	public int hashCode() {
		return 37 * alloc.hashCode() + context.hashCode();
	}

	@Override
	public boolean hasFieldSelector(FieldElem field) {
		return false;
	}

}
