package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import chord.program.Program;
import framework.scuba.helper.ArgDerivedHelper;

public class Env {

	// this is the global fields factory (StaticElem, e.g. A.f)
	public final static Map<StaticElem, StaticElem> staticElemFactory = new HashMap<StaticElem, StaticElem>();

	// global fields factory (NormalFieldElem, e.g. f/g)
	public final static Map<NormalFieldElem, NormalFieldElem> normalFieldElemFactory = new HashMap<NormalFieldElem, NormalFieldElem>();

	public final static Map<StaticAccessPath, StaticAccessPath> staticAPFactory = new HashMap<StaticAccessPath, StaticAccessPath>();

	public final static Map<ProgramPoint, ProgramPoint> progPointFactory = new HashMap<ProgramPoint, ProgramPoint>();

	public static int countAccessPath = 0;

	// Callgraph instance.
	public static CallGraph cg;

	/**
	 * This map holds all key,value pairs such that value.getSuperclass() ==
	 * key. This is one of the three maps that hold the inverse of the
	 * relationships given by the getSuperclass and getInterfaces methods of
	 * SootClass.
	 */
	protected final static Map<jq_Class, List<jq_Class>> classToSubclasses = new HashMap<jq_Class, List<jq_Class>>();

	// TODO: we still need to consider invokeinterface!
	public final static Map<jq_Class, Integer> class2Term = new HashMap<jq_Class, Integer>();

	// get the StaticElem given the declaring class and the corresponding field
	// in the IR
	public static StaticElem getStaticElem(jq_Class clazz, jq_Field field) {
		// create a wrapper
		StaticElem ret = new StaticElem(clazz, field);
		// try to look up this wrapper in the factory
		if (staticElemFactory.containsKey(ret)) {
			return staticElemFactory.get(ret);
		}
		// not found in the factory
		// every time generating a staticElem, do this marking
		ArgDerivedHelper.markArgDerived(ret);
		staticElemFactory.put(ret, ret);

		return ret;
	}

	public static ProgramPoint getProgramPoint(jq_Class clazz,
			jq_Method method, int line) {
		ProgramPoint ret = new ProgramPoint(clazz, method, line);
		if (progPointFactory.containsKey(ret)) {
			return progPointFactory.get(ret);
		}
		progPointFactory.put(ret, ret);

		return ret;
	}

	public static NormalFieldElem getNormalFieldElem(jq_Field field) {
		NormalFieldElem ret = new NormalFieldElem(field);
		if (normalFieldElemFactory.containsKey(ret)) {
			return normalFieldElemFactory.get(ret);
		}
		normalFieldElemFactory.put(ret, ret);
		return ret;
	}

	// get the AccessPath whose base is StaticElem
	public static StaticAccessPath getStaticAccessPath(StaticElem base,
			FieldElem field) {

		StaticAccessPath ret = new StaticAccessPath(base, field,
				Env.countAccessPath++);
		if (staticAPFactory.containsKey(ret)) {
			return (StaticAccessPath) staticAPFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		staticAPFactory.put(ret, ret);

		return ret;
	}

	// get the AccessPath whose base is HeapObject
	public static StaticAccessPath getStaticAccessPath(StaticAccessPath base,
			FieldElem field) {
		StaticAccessPath ret = new StaticAccessPath(base, field,
				Env.countAccessPath++);
		if (staticAPFactory.containsKey(ret)) {
			return (StaticAccessPath) staticAPFactory.get(ret);
		}

		ArgDerivedHelper.markArgDerived(ret);
		staticAPFactory.put(ret, ret);

		return ret;
	}

	public static StaticAccessPath getStaticAccessPath(StaticAccessPath other) {
		if (staticAPFactory.containsKey(other)) {
			return (StaticAccessPath) staticAPFactory.get(other);
		}

		ArgDerivedHelper.markArgDerived(other);
		staticAPFactory.put(other, other);

		return other;
	}

	/**
	 * Return the unique number for a class as a term.
	 * 
	 * @param cls
	 * @return
	 */
	public static int getConstTerm4Class(jq_Class cls) {
		return class2Term.get(cls);
	}

	private static void put(Map<jq_Class, List<jq_Class>> m, jq_Class key,
			jq_Class value) {
		List<jq_Class> l = m.get(key);
		if (l == null)
			m.put(key, l = new ArrayList<jq_Class>());
		l.add(value);
	}

	/** Constructs a hierarchy from the current scene. */
	public static void buildClassHierarchy() {
		/* First build the inverse maps. */
		for (jq_Reference r : Program.g().getClasses()) {
			if (r instanceof jq_Array)
				continue;
			final jq_Class cl = (jq_Class) r;
			if (!cl.isInterface() && (cl.getSuperclass() != null)) {
				put(classToSubclasses, cl.getSuperclass(), cl);
			}
		}

		/* Now do a post order traversal to get the numbers. */
		jq_Reference rootObj = Program.g().getClass("java.lang.Object");
		assert rootObj != null : "Fails to load java.lang.Object";
		pfsVisit(1, (jq_Class) rootObj);
	}

	/**
	 * @param clz
	 * @return Return the classes that directly inherit clz
	 */
	public static List<jq_Class> getSuccessors(jq_Class clz) {
		return classToSubclasses.get(clz);
	}

	/**
	 * post-order visit the whole class hierarchy.
	 * 
	 * @param start
	 *            :uniqie number for each class order by class hierarchy.
	 * @param c
	 *            : current class.
	 * @return
	 */
	protected static int pfsVisit(int start, jq_Class c) {
		if (classToSubclasses.get(c) != null) {
			for (jq_Class subCls : classToSubclasses.get(c)) {
				if (subCls.isInterface())
					continue;
				start = pfsVisit(start, subCls);
			}
		}
		if (c.isInterface()) {
			throw new RuntimeException("Attempt to pfs visit interface " + c);
		}
		class2Term.put(c, start);

		start++;
		return start;
	}
}
