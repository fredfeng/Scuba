package test.interproc;

public class A {    
	
	A next;
	Object f;
	
	public void test1() {
		this.next = new A();
		this.next.f = new Object();
		this.next.next = new A();
		this.next.next.f = new Object();
		A ret = test2(5);
		
	}
	
	public A test2(int counter) {
		if(counter==0) return this;
		return this.next.test2(counter--);
		
	} 
	
    public void foo()
    {
       System.out.println("In A:foo");
       this.goo();
       this.bar();    
    }
    
    public void bar()
    {
       System.out.println("In A:bar");      
    }
    
    public void goo() {
        System.out.println("In A:goo");      
    }
}