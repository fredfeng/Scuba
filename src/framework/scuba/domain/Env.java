package framework.scuba.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joeq.Class.jq_Array;
import joeq.Class.jq_Class;
import joeq.Class.jq_Field;
import joeq.Class.jq_Method;
import joeq.Class.jq_Reference;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.analyses.alias.CICG;
import chord.program.Program;

public class Env {

	// Callgraph instance.
	public static CICG cg;

	/**
	 * This map holds all key,value pairs such that value.getSuperclass() ==
	 * key. This is one of the three maps that hold the inverse of the
	 * relationships given by the getSuperclass and getInterfaces methods of
	 * SootClass.
	 */
	protected final static Map<jq_Class, List<jq_Class>> classToSubclasses = new HashMap<jq_Class, List<jq_Class>>();

	// TODO: we still need to consider invoke interface!
	public final static Map<jq_Class, Integer> class2Term = new HashMap<jq_Class, Integer>();

	public final static Map<jq_Class, Integer> class2Min = new HashMap<jq_Class, Integer>();

	public final static Map<Integer, jq_Class> class2TermRev = new HashMap<Integer, jq_Class>();

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
			if (!cl.isInterface() && (cl.getSuperclass() != null))
				put(classToSubclasses, cl.getSuperclass(), cl);
		}

		/* Now do a post order traversal to get the numbers. */
		jq_Reference rootObj = Program.g().getClass("java.lang.Object");
		assert rootObj != null : "Fails to load java.lang.Object";
		pfsVisit(1, (jq_Class) rootObj);
	}

	/**
	 * This is used to generate interval for a given class. here we return the
	 * number of subclass which have the minimal number. if no subclass, return
	 * its own number.
	 */
	public static int getMinSubclass(jq_Class clz) {
		if (class2Min.get(clz) != null)
			return class2Min.get(clz);

		jq_Class[] subClaz = clz.getSubClasses();
		if (subClaz.length == 0) {
			int self = class2Term.get(clz);
			class2Min.put(clz, self);
			return self;
		}

		int min = class2Term.get(clz);
		LinkedHashSet<jq_Class> wl = new LinkedHashSet<jq_Class>();
		wl.add(clz);
		while (!wl.isEmpty()) {
			jq_Class sub = wl.iterator().next();
			wl.remove(sub);
			int subInt = class2Term.get(sub);
			// subclass is unreachable.
			if (class2Term.get(sub) == null)
				continue;

			if (subInt < min)
				min = subInt;

			jq_Class[] succs = sub.getSubClasses();
			for (int i = 0; i < succs.length; i++) {
				jq_Class succ = succs[i];
				if (class2Term.get(succ) != null)
					wl.add(succ);
			}
		}
		// cache the result.
		class2Min.put(clz, min);
		return min;
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
		class2TermRev.put(start, c);

		start++;
		return start;
	}

	// ----------------- AbsHeap Operations ----------
	public static Context getContext(ProgPoint point, Context prevCtx) {
		return ContextFactory.getContext(point, prevCtx);
	}

	public static ProgPoint getProgPoint(Quad stmt) {
		return ProgPointFactory.getProgPoint(stmt);
	}

	public static LocalVarElem getLocalVarElem(Register r, jq_Method meth,
			jq_Class clazz, jq_Type type) {
		return LocalVarElemFactory.getLocalVarElem(r, meth, clazz, type);
	}

	public static LocalVarElem findLocalVarElem(Register r) {
		return LocalVarElemFactory.findLocalVarElem(r);
	}

	public static RetElem getRetElem(jq_Method meth) {
		return RetElemFactory.getRetElem(meth);
	}

	public static PrimitiveElem getPrimitiveElem() {
		return PrimitiveElemFactory.getPrimitiveElem();
	}

	public static StaticFieldElem getStaticFieldElem(jq_Field staticField) {
		return StaticFieldElemFactory.getStaticFieldElem(staticField);
	}

	public static ParamElem getParamElem(Register r, jq_Method meth,
			jq_Class clazz, jq_Type type) {
		return ParamElemFactory.getParamElem(r, meth, clazz, type);
	}

	public static AllocElem getAllocElem(Quad stmt, jq_Type type) {
		Context ctx = ContextFactory.getContext(null, null);
		return AllocElemFactory.getAllocElem(stmt, type, ctx);
	}

	public static AllocElem getAllocElem(Quad stmt, jq_Type type, Context ctx) {
		return AllocElemFactory.getAllocElem(stmt, type, ctx);
	}

	public static LocalAccessPathElem getLocalAccessPathElem(AbsMemLoc inner,
			FieldElem outer) {
		return LocalAPElemFactory.getLocalAccessPathElem(inner, outer);
	}

	public static StaticAccessPathElem getStaticAccessPathElem(AbsMemLoc inner,
			FieldElem outer) {
		return StaticAPElemFactory.getStaticAccessPathElem(inner, outer);
	}

	public static EpsilonFieldElem getEpsilonFieldElem() {
		return EpsilonFieldElemFactory.getEpsilonFieldElem();
	}

	public static IndexFieldElem getIndexFieldElem() {
		return IndexFieldElemFactory.getIndexFieldElem();
	}

	public static RegFieldElem getRegFieldElem(jq_Field field) {
		return RegFieldElemFactory.getRegFieldElem(field);
	}

	// ---------- propagation set -------------
	public static Set<AbsMemLoc> toProp = new HashSet<AbsMemLoc>();

	public static boolean toProp(AbsMemLoc loc) {
		boolean ret = false;
		if (loc instanceof ParamElem || loc instanceof StaticFieldElem
				|| loc instanceof AccessPathElem || loc instanceof RetElem) {
			ret = true;
		} else if (loc instanceof LocalVarElem || loc instanceof AllocElem) {
			if (toProp.contains(loc)) {
				ret = true;
			} else {
				ret = false;
			}
		} else {
			assert false : "wrong!";
		}
		return ret;
	}

}