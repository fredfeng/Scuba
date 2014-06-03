package framework.scuba.domain;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class Context {

	final protected LinkedList<ProgramPoint> pointsSeq = new LinkedList<ProgramPoint>();

	final protected Set<ProgramPoint> points = new HashSet<ProgramPoint>();

	public Context() {

	}

	public Context(LinkedList<ProgramPoint> pointsSeq, Set<ProgramPoint> points) {
		this.pointsSeq.addAll(pointsSeq);
		this.points.addAll(points);
	}

	public Context(ProgramPoint point) {
		this.pointsSeq.add(point);
		this.points.add(point);
	}

	public void appendFront(ProgramPoint point) {
		pointsSeq.addFirst(point);
		points.add(point);
	}

	public void appendEnd(ProgramPoint point) {
		pointsSeq.addLast(point);
		points.add(point);
	}

	public void appendEnd(Context otherCtx) {
		pointsSeq.addAll(otherCtx.getContext());
		points.addAll(otherCtx.getProgramPoints());
	}

	public boolean contains(ProgramPoint point) {
		return points.contains(point);
	}

	public int size() {
		return pointsSeq.size();
	}

	public Set<ProgramPoint> getProgramPoints() {
		return this.points;
	}

	public LinkedList<ProgramPoint> getContext() {
		return this.pointsSeq;
	}

	public ProgramPoint getProgramPoint(int index) {
		return pointsSeq.get(index);
	}

	@Override
	public Context clone() {
		Context ret = new Context(pointsSeq, points);
		return ret;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Context))
			return false;

		Context otherCtx = (Context) other;

		if (pointsSeq.size() != otherCtx.size())
			return false;

		for (int i = 0; i < pointsSeq.size(); i++) {
			ProgramPoint thisPoint = pointsSeq.get(i);
			ProgramPoint otherPoint = otherCtx.getProgramPoint(i);
			if (!thisPoint.equals(otherPoint))
				return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		if (pointsSeq.isEmpty())
			return 0;

		int ret = 0;
		int range = 3;
		for (int i = 0; i < pointsSeq.size(); i++) {
			ret *= 37;
			ret += pointsSeq.get(i).hashCode();
			if (i > range)
				break;
		}

		return ret;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[Ctx]");
		for (ProgramPoint point : pointsSeq) {
			sb.append(point + "##");
		}
		return sb.toString();
	}
}
