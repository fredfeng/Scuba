package framework.scuba.domain;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class Context {

	protected LinkedList<ProgramPoint> context;

	protected Set<ProgramPoint> points;

	public Context() {

	}

	public Context(ProgramPoint point) {
		this.context = new LinkedList<ProgramPoint>();
		this.context.add(point);
		this.points = new HashSet<ProgramPoint>();
		this.points.add(point);
	}

	public void appendFront(ProgramPoint point) {
		context.addFirst(point);
		points.add(point);

	}

	public void appendEnd(ProgramPoint point) {
		context.addLast(point);
		points.add(point);
	}

	public boolean contains(ProgramPoint point) {
		return points.contains(point);
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
