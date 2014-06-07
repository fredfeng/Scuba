package test.interproc;

//Xinyu's example to demo why we need fix point.
public class TestInter2 {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new TestInter2().run();
	}

	void run() {
		A x = new A();
		A y = x;
		foo(x, y);
	}
	
	void foo(A x, A y){
		int i = 0;
		while(i < 10) {
			i++;
			B t = x.f;
			y.f = new B();
		}
	}
	
	class A {
		B f;
	}
	
	class B{
		
	}
}
