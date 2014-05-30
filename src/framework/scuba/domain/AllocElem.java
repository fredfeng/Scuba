package framework.scuba.domain;

public class AllocElem extends HeapObject {

	protected AllocSite allocSite;

	protected Context context;

	public AllocElem(AllocSite allocSite, Context context) {
		this.allocSite = allocSite;
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

	@Override
	public String toString() {
		return allocSite + " || " + context;
	}

	@Override
	public boolean equals(Object other) {

		return (other instanceof AllocElem)
				&& (allocSite.equals(((AllocElem) other).allocSite))
				&& (context.equals(((AllocElem) other).context));
	}

	@Override
	public int hashCode() {
		return 37 * allocSite.hashCode() + context.hashCode();
	}

}
