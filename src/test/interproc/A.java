package test.interproc;

public class A {

	Object f;

	void foo(Object o) {
		this.f = o;
	}

	public static void bar(A a) {
		Object o = new Object();
		a.foo(o);
	}

}
