package com.helper;


import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ZMQServer<I,O>  implements Callable<Boolean> {

    ZMQ.Context ctx;
    ZMQ.Socket server;
    ByteArrayInputStream bis;
    ObjectInput in = null;
    //double[] o = null;
    I o = null;
    O output = null;
    Class service;
    String serviceName;
    Object servieObj;



    public ZMQServer( String className, String serviceName) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        this.serviceName = serviceName;
        this.service = ZMQServer.class.getClassLoader().loadClass(className);
        this.servieObj = this.service.newInstance();

    }

    @Override
    public Boolean call(){
        this.ctx = ZMQ.context(1);
        this.server = ctx.socket(ZMQ.REP);
        this.server.bind("ipc://"+serviceName);
        System.out.println("start runnung " + serviceName + " server");

        while(!Thread.currentThread().isInterrupted()){


            ZMsg recvMsg = ZMsg.recvMsg(this.server);
            System.out.println("Msg size:" + recvMsg.size());
            if(recvMsg.size()>=1){

                //lese Nachricht aus
                Iterator<ZFrame> frames = recvMsg.iterator();
                while(frames.hasNext()){
                    ZFrame frame = frames.next();

                    bis =  new ByteArrayInputStream(frame.getData());
                    try {
                        in = new ObjectInputStream(bis);
                        o = ((I) in.readObject());


                        Method remoteMethod = service.getDeclaredMethods()[0];
                        Object out = remoteMethod.invoke(this.servieObj,o);
                        this.output = (O) out;
                        System.out.println("Return Type: " + output.getClass());


                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }

            }

            //Send Server reply

            byte[] returnByte = null;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = null;


                out = new ObjectOutputStream(bos);
                out.writeObject(this.output);
                out.flush();
                //System.out.println("ReturnByte [] size "+returnByte.length);
                returnByte = bos.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("send");
            this.server.send(returnByte);

            System.out.println(returnByte.length);



        }


        return true;
    }

    /**
    public static void main(String... args){

        ExecutorService executor = Executors.newFixedThreadPool(2);
        //executor.submit(new ZMQServer<Double[],Double>());


    }*/


}
