package com.gen;


import com.helper.ProxyClient;
import com.helper.ZMQServer;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PoetGen {

    public void genClientClass(String moduleName, Class providerClass, String desDir){
        //Method[] methods = providerClass.getDeclaredMethods();

        Method[] methods = providerClass.getInterfaces()[0].getDeclaredMethods();

        Set<MethodSpec> proxyMethodSet = new HashSet<>();

        MethodSpec[] proxyMethods = new MethodSpec[methods.length];
        for(Method m: methods){
            proxyMethodSet.add(genProxyMethod(m,moduleName));
        }

        TypeSpec proxyClass = genClass(providerClass.getSimpleName(),providerClass.getInterfaces(),proxyMethodSet);
        try {
            saveJavaFile(moduleName,providerClass.getPackageName(),proxyClass, desDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }




    public void genServerClass( Set<Class> serviceClass,String desDir){

        //Method method = serviceClass.getDeclaredMethods()[0];

       MethodSpec.Builder mainBuilder = MethodSpec.methodBuilder("main")
               .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
               .returns(void.class)
               .addParameter(String[].class, "args")
               .addStatement("$T<$T<$T>> serverList = new $T<>()",List.class, Callable.class, Boolean.class, LinkedList.class)
               .addStatement("$T executor = $T.newFixedThreadPool($L)", ExecutorService.class, Executors.class,serviceClass.size());
        for(Class clazz:serviceClass){
            Method method = clazz.getDeclaredMethods()[0];
            mainBuilder.addStatement("serverList.add(new $T<$T,$T>($S,$S))"
                    ,ZMQServer.class, method.getParameterTypes()[0], getWrapper(method.getReturnType()),
                    clazz.getName(), clazz.getModule().getName());
        }
        mainBuilder.addStatement("executor.invokeAll(serverList)");
        mainBuilder
                .addException(ClassNotFoundException.class)
                .addException(IllegalAccessException.class)
                .addException(InstantiationException.class)
                .addException(InterruptedException.class);



        TypeSpec serverClass = TypeSpec.classBuilder("Server").addModifiers(Modifier.PUBLIC).addMethod(mainBuilder.build()).build();
        JavaFile file = JavaFile.builder("com",serverClass).build();

        try {
            file.writeTo(Paths.get(desDir));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    private MethodSpec genProxyMethod(Method superMethod, String moduleName){

        Set<ParameterSpec> parr = new HashSet<>();
        int i = 0;
        for(Class c:superMethod.getParameterTypes()){
            String arg = "arg"+i;
            parr.add(ParameterSpec.builder(c,arg).build());
            i++;
        }

        Set<TypeName> exSet =  new HashSet<>();
        for(Class ex:superMethod.getExceptionTypes()){
            exSet.add(ParameterizedTypeName.get(ex));
        }

        Iterable<ParameterSpec> param = parr;
        Iterable<TypeName> exceptions = exSet;

        Class returnType = superMethod.getReturnType();
        String methodNam = superMethod.getName();


        Class genType =(returnType.isPrimitive()) ? getWrapper(returnType) : returnType;

        return MethodSpec.methodBuilder(methodNam).addModifiers(Modifier.PUBLIC)
                .returns(returnType)
                .addParameters(param)
                .addAnnotation(Override.class)
                .addStatement("$T<$T> client = new $T<>($S)", ProxyClient.class,genType ,ProxyClient.class,moduleName)
                .addStatement("return client.send(arg0)")
                .addExceptions(exceptions) // from interface
                .build();

    }

    private TypeSpec genClass(String className, Class[] superInterfaces, Set<MethodSpec> methods) {
        Set<TypeName> interfaces = new HashSet<>();

        for(Class c:superInterfaces){
            if(c==null) break;

            interfaces.add(ParameterizedTypeName.get(c));
        }

        Iterable<TypeName> itrInterfaces= interfaces;
        Iterable<MethodSpec> itrMethods = methods;



        TypeSpec.Builder builder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC);
        if(methods.size()>=1){
            builder
            .addSuperinterfaces(itrInterfaces)
            .addMethods(itrMethods);

        }

        return builder.build();
    }


    private void saveJavaFile(String modulName, String packageName, TypeSpec clazz, String destDir) throws IOException {

        JavaFile file = JavaFile.builder(packageName,clazz).build();
        file.writeTo(Paths.get(destDir));

    }


    private Class getWrapper(Class primitiv){
        if(primitiv.isPrimitive() ){
            switch (primitiv.getSimpleName()) {
                case "double":
                    return Double.class;

                case "int":
                    return Integer.class;

                case "char":
                    return Character.class;

                case "float":
                    return Float.class;

                case "boolean":
                    return Boolean.class;

                case "long":
                    return Long.class;

                default:
                    return null;
            }
        }
        else {
            return primitiv;
        }
    }



}
