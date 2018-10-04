package com.helper;

import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import java.io.*;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.logging.Logger;


public class ProxyClient<R> {
    final static Logger LOGGER = Logger.getLogger(ProxyClient.class.getName());
    private final static int REQUEST_TIMEOUT = 2500;    //  msecs, (> 1000!)
    private final static int REQUEST_RETRIES = 3;       //  Before we abandon
    private static String SERVER_ENDPOINT;


    private static ZContext ctx;
    private static Socket client;
    private Object defaultObj;

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutput out = null;
    byte[] byteArr= null;
    Optional<R> opt;


    public ProxyClient(String serviceName){


        this.SERVER_ENDPOINT = "ipc://"+serviceName;
        this.ctx = new ZContext();
        this.client = ctx.createSocket(ZMQ.REQ);
        assert (client != null);
        //build connection
        client.connect(SERVER_ENDPOINT);
    }

    public R send(Object obj)  {

        LOGGER.info("client was invoked");
        if(!(obj instanceof Serializable))
            LOGGER.severe("Object " +obj.toString() + " cant be used for communication, it don't implements the <Serializable> interface");

        //Serialisierung  des Obj und senden der Msg
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            byteArr = bos.toByteArray();

            ZMsg reqMsg = new ZMsg();
            reqMsg.add(new ZFrame(byteArr));
            opt = Optional.ofNullable(invoke(reqMsg));
            if(opt.isPresent()){
                LOGGER.info("optional should be present");
                return opt.get();
            }else{
                LOGGER.warning("cant reach service -> return null");
                return null;
            }
            //LOGGER.info("Value is null: "+ (value==null));


        }catch (IOException ex){

        }
        LOGGER.warning("serialization has failed -> return null ");
        return null;

    }

    public R invoke(ZMsg input) {

        int retriesLeft = REQUEST_RETRIES;
        while (retriesLeft>0 && !Thread.currentThread().isInterrupted()){
            //System.out.println("Msg size:" + input.size());
            input.send(client);

            int expect_reply = 1;
            while (expect_reply > 0) {
                //  Poll socket for a reply, with timeout
                ZMQ.PollItem items[]  = {new ZMQ.PollItem(client, ZMQ.Poller.POLLIN)};
                items[0] = new ZMQ.PollItem(client, ZMQ.Poller.POLLIN);
                Selector selector = null;
                try {
                    selector = Selector.open();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int rc = ZMQ.poll(selector,items, REQUEST_TIMEOUT);

                if (rc == -1) break;          //  Interrupted

                if (items[0].isReadable()) {
                    //  We got a reply from the server, must match sequence
                    byte[]reply = client.recv();



                    if (reply == null)
                        break;      //  Interrupted


                    if (reply.length>0) {
                        ByteArrayInputStream bis = new ByteArrayInputStream(reply);
                        ObjectInput in = null;
                        Object o = null;
                        try {
                            in = new ObjectInputStream(bis);
                            o = in.readObject();

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (in != null) {
                                    in.close();
                                }
                            } catch (IOException ex) {
                                // ignore close exception
                            }
                        }

                        return (R)o;

                        //retriesLeft = 0 ;
                        //expect_reply = 0;

                    }   //else
                        //System.out.printf("E: malformed reply from server: %s\n",
                        //       reply);

                } else if (--retriesLeft == 0) {
                    //System.out.println("E: server seems to be offline, abandoning\n");

                    break;
                } else {
                    //System.out.println("W: no response from server, retrying\n");
                    //  Old socket is confused; close it and open a new one
                    ctx.destroySocket(client);

                    LOGGER.info("reconnecting to server");
                    client = ctx.createSocket(ZMQ.REQ);
                    client.connect(SERVER_ENDPOINT);
                    //  Send request again, on new socket
                    input.send(client);
                }

            }


        }

        return null;
    }




}
