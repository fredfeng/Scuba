package framework.scuba.domain;

import java.util.LinkedList;

public class Context {

	protected LinkedList<ProgramPoint> context;

	public Context() {

	}

	public Context(ProgramPoint point) {
		this.context = new LinkedList<ProgramPoint>();
		this.context.add(point);
	}

	public void appendFront(ProgramPoint point) {

		this.context.addFirst(point);

	}

	public void appendEnd(ProgramPoint point) {

		this.context.addLast(point);
	}

	public int size() {
		return context.size();
	}

	public ProgramPoint get(int index) {
		return context.get(index);
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Context))
			return false;

		Context otherCtx = (Context) other;

		if (context.size() != otherCtx.size())
			return false;

		for (int i = 0; i < context.size(); i++) {
			ProgramPoint thisPoint = context.get(i);
			ProgramPoint otherPoint = otherCtx.get(i);
			if (!thisPoint.equals(otherPoint))
				return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		if (context.isEmpty())
			return 0;

		int ret = 0;
		int range = 3;
		for (int i = 0; i < context.size(); i++) {
			ret *= 37;
			ret += context.get(i).hashCode();
			if (i > range)
				break;
		}

		return ret;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[Context]");
		for (ProgramPoint point : context) {
			sb.append(point + "@");
		}
		return sb.toString();
	}
}
