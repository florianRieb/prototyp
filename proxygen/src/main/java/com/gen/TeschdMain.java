package com.gen;

import com.helper.ProxyClient;

import java.util.Optional;


public class TeschdMain {

    public static void main(String... args) throws Exception {


        Optional<Double> opt = Optional.ofNullable(testmethode());
        System.out.println(opt.isPresent());

    }


    static Double testmethode(){
        Double[] array = {1.0,5.0,10.5};
        ProxyClient<Double> client = new ProxyClient<>("service.a");
        return client.send(array);



    }

}
