package com.gen;

public class Edge<X,Y> {
    private  X baseModule;
    private  Y module2Isolate;

    Edge(X x, Y module2Isolate){
        this.baseModule = x;
        this.module2Isolate = module2Isolate;
    }
    Edge(){

    }

    public void setBaseModule(X x) {
        this.baseModule = x;
    }

    public void setModule2Isolate(Y module2Isolate) {
        this.module2Isolate = module2Isolate;
    }

    public X getBaseModule() {
        return baseModule;
    }

    public Y getModule2Isolate() {
        return module2Isolate;
    }

    @Override
    public String toString(){
        return "(moduleA" + baseModule +" : moduleB" + module2Isolate + ")";
    }
}
