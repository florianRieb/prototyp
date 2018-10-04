package com.gen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class RuntimeExecutor{



    public RuntimeExecutor(){


    }


    public boolean mvnCompile(String projectDir){
        Process p = null;
        String s = null;
        try {
            p = Runtime.getRuntime().exec("mvn package -f " +projectDir + " -DoutputDirectory="+projectDir+"/mlib ");

             BufferedReader stdInput = new BufferedReader(new
             InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));


             while ((s = stdInput.readLine()) != null) {
             System.out.println(s);
             }


            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;

        }


    }

    public boolean execSkript() throws IOException {

        Process p = null;
        String s = null;

        try {
            p = Runtime.getRuntime().exec("sh "+Main.projectDir+"generatedFiles/skript.sh");

            /**
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));
            */
            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));

            /**
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
            */

            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;

        }
    }
}
