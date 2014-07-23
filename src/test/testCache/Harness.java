package test.testCache;

public class Harness {

	public static void main(String[] args) {
		A a = new A();
		C ret = null;
		for (int i = 0; i < 10; i++) {
			a.f = new B();
			ret = a.f.foo();
			a.f = new BB();
		}
	}
}

class A {
	B f;
}

class B {
	C g;

	C foo() {
		return g = new C();
	}
}

class BB extends B {
	C g;

	C foo() {
		return g = new C();
	}
}

class C {

}
