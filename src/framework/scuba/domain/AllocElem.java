package framework.scuba.domain;

public class AllocElem extends HeapObject {

	final protected Alloc alloc;

	final protected Context context;

	public AllocElem(Alloc allocSite, Context context) {
		this.alloc = allocSite;
		this.context = context;
	}

	public AllocElem findRoot() {
		return this;
	}

	public void appendContextFront(ProgramPoint point) {
		this.context.appendFront(point);
	}

	public void appendContextEnd(ProgramPoint point) {
		this.context.appendEnd(point);
	}

	public int length() {
		return context.length();
	}

	public Alloc getAlloc() {
		return alloc;
	}

	public Context getContext() {
		return context;
	}

	public boolean contains(ProgramPoint point) {
		return context.contains(point);
	}

	@Override
	public String toString() {
		return alloc + "||" + context;
	}

	@Override
	public String dump() {
		return alloc + "||" + context;
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

	@Override
	public int countFieldSelector(FieldElem field) {
		return 0;
	}

	@Override
	public AllocElem clone() {
		return new AllocElem(alloc, context.clone());
	}

}
