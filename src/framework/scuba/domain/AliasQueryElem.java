package framework.scuba.domain;

import chord.util.tuple.object.Pair;

public class AliasQueryElem {
	// the local variable that MAY alias
	protected AbstractMemLoc loc1, loc2;
	// the program point the alias query is inserted in
	protected ProgramPoint point;

	public AliasQueryElem(AbstractMemLoc loc1, AbstractMemLoc loc2,
			ProgramPoint point) {
		this.loc1 = loc1;
		this.loc2 = loc2;
		this.point = point;
	}

	public ProgramPoint getPPoint() {
		return point;
	}

	public AbstractMemLoc getLoc1() {
		return loc1;
	}

	public AbstractMemLoc getloc2() {
		return loc2;
	}

	public Pair<AbstractMemLoc, AbstractMemLoc> getLocs() {
		return new Pair<AbstractMemLoc, AbstractMemLoc>(loc1, loc2);
	}

	@Override
	public int hashCode() {
		return 37 * point.hashCode() + loc1.hashCode() + loc2.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return (other instanceof AliasQueryElem)
				&& (loc1.equals(((AliasQueryElem) other).loc1))
				&& (loc2.equals(((AliasQueryElem) other).loc2))
				&& (point.equals(((AliasQueryElem) other).point));
	}

	@Override
	public String toString() {
		return "[AQ]" + "(" + loc1 + "," + loc2 + ") at " + "(" + point + ")";
	}
}
