package framework.scuba.domain;

import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;

public class AllocElem extends HeapObject {

	// the site where this allocElem is originally allocated
	final protected Quad site;

	// the context this allocElem goes through
	final protected Context context;

	public AllocElem(Quad site, Context context, jq_Type type, int number) {
		super(type, number);
		this.site = site;
		this.context = context;
	}

	public Quad getSite() {
		return site;
	}

	public Context getContext() {
		return context;
	}

	public int ctxtLength() {
		return context.length();
	}

	public boolean contains(ProgPoint point) {
		return context.contains(point);
	}

	// ------------ Regular --------------
	@Override
	public String toString() {
		return "[A: " + site + " | " + context + "]";
	}

	@Override
	public boolean equals(Object other) {
		return this == other;
	}

	@Override
	public int hashCode() {
		return number;
	}

}