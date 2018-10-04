package com.gen;

import freemarker.template.Configuration;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.*;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Path;
import java.util.*;

public class TempGenerator {

    Configuration cfg;

    public TempGenerator(){
        //Freemarker Test
       this.cfg = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_28);
        try {
            cfg.setDirectoryForTemplateLoading(new File(System.getProperty("user.dir")+"/proxygen/src/main/resources/templates"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
    }

    public void createServerModuleInfo(String moduleName, Set<Module> modules, String fileDest, Set<Class> exportClasses) throws IOException {
        //Create Data Model
        Map<String,Object> root = new HashMap<>();
        Set<String> requires= new HashSet<>();
        Set<String> exports = new HashSet<>();
        Set<String> opens = new HashSet<>();
        Set<String> provides = new HashSet<>();




        for(Module module: modules){
            module.getDescriptor().requires().stream().forEach((ModuleDescriptor.Requires req) -> requires.add(req.name()));
            module.getDescriptor().exports().stream().forEach((ModuleDescriptor.Exports exp)-> exports.add(((exp.isQualified()) ? exp.source()+" to" + exp.targets(): exp.source())));
            //module.getDescriptor().provides().stream().forEach((ModuleDescriptor.Provides p) -> provides.add(p.toString().replace("]","").replace("[","")));
        }

        for(Class c:exportClasses){
            String packageName = c.getPackageName();
            if(!exports.contains(packageName))
                exports.add(packageName + " to " + "libmodule");

        }

        //Ob CLient oder Server benötigen beide Module das proxygen Modul um auf die Helper zuzugreifen
        requires.add("libmodule");

        root.put("moduleName",moduleName);
        root.put("requires",requires);
        root.put("exports", exports);
        root.put("provides", provides);

        //lade Module-info Template
        Template temp = cfg.getTemplate("module-info.ftl");


        File moduleFile = new File(fileDest+ "/module-info.java");
        //moduleFile.getParentFile().mkdir();


        try(Writer osw = new OutputStreamWriter(new FileOutputStream(moduleFile))){
            temp.process(root,osw);
        }catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }



    public String cloneModuleInfo(ModuleDescriptor desc, String fileDest, String exportPackageName) throws IOException {

        //Create Data Model
        Map<String,Object> root = new HashMap<>();
        Set<String> requires= new HashSet<>();
        Set<String> exports = new HashSet<>();
        Set<String> opens = new HashSet<>();
        Set<String> provides = new HashSet<>();
        String moduleNameExtention = "_client";

        desc.requires().stream().forEach((ModuleDescriptor.Requires req) -> requires.add(req.name()));
        desc.exports().stream().forEach((ModuleDescriptor.Exports exp)-> exports.add(((exp.isQualified()) ? exp.source()+" to" + exp.targets(): exp.source())));
        desc.provides().stream().forEach((ModuleDescriptor.Provides p) -> provides.add(p.toString().replace("]","").replace("[","")));

        //Füge export für das Server Module inzu, wird benötigt, dass ZMQServer auf die ServiceImpl Klasse zugreifen kann
        if(!exportPackageName.equals("")){
            moduleNameExtention = "_server";
            if(!exports.contains(exportPackageName))
                exports.add(exportPackageName + " to " + "libmodule");
        }

        //Ob CLient oder Server benötigen beide Module das proxygen Modul um auf die Helper zuzugreifen
        requires.add("libmodule");

        root.put("moduleName",desc.name()+moduleNameExtention);
        root.put("requires",requires);
        root.put("exports", exports);
        root.put("provides", provides);

        //lade Module-info Template
        Template temp = cfg.getTemplate("module-info.ftl");


        File moduleFile = new File(fileDest+ "/module-info.java");
        moduleFile.getParentFile().mkdir();


        try(Writer osw = new OutputStreamWriter(new FileOutputStream(moduleFile))){
            temp.process(root,osw);
        }catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return desc.name()+moduleNameExtention;

    }

    public void skript(String projectDir, Set<String> clientMods, Set<String> envMods) throws IOException {
        Map<String,Object> root = new HashMap<>();
        List<String> modules = new LinkedList<>();

        root.put("clientModules", clientMods);
        root.put("envModules",envMods);
        root.put("projDir",projectDir);


        Template temp = cfg.getTemplate("compile.ftl");

        File compileFile = new File(projectDir + File.separator + "generatedFiles" + File.separator +"skript.sh");

        try(Writer osw = new OutputStreamWriter(new FileOutputStream(compileFile))){
            temp.process(root,osw);
        }catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String dockerServerFile(String destDir, String modulSourceDir, String moduleName) throws IOException {
        Map<String,Object> root = new HashMap<>();
        root.put("moduleName",moduleName);
        root.put("moduleLib", modulSourceDir);

        Template temp = cfg.getTemplate("dockerServerTemp.ftl");
        File dockerFile = new File(destDir + File.separator + "dockerfile");

        try(Writer osw = new OutputStreamWriter(new FileOutputStream(dockerFile))){
            temp.process(root,osw);
        }catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dockerFile.getPath();

    }

    public String dockerMainAppFile(String destDir,String args, Set<String> services) throws IOException {
        Map<String,Object> root = new HashMap<>();
        root.put("args",args);
        root.put("services",services);

            Template temp = cfg.getTemplate("dockerMainAppTemp.ftl");
            File dockerFile = new File(destDir + File.separator + "dockerfile");

        try(Writer osw = new OutputStreamWriter(new FileOutputStream(dockerFile))){
            temp.process(root,osw);
        }catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dockerFile.getPath();


    }

    public void createYAML(String destDir, Map<String,String> dockerfiles, String rootModuleName) throws IOException {
        Map<String,Object> root = new HashMap<>();
        List<String> name = new LinkedList<>();
        List<String> environments = new LinkedList<>();

        root.put("rootModuleName",rootModuleName);
       environments.addAll(dockerfiles.keySet());
       root.put("environments",environments);



        Template temp = cfg.getTemplate("appYAML.ftl");
        File dockerFile = new File(destDir + File.separator + rootModuleName+".yaml");

        try(Writer osw = new OutputStreamWriter(new FileOutputStream(dockerFile))){
            temp.process(root,osw);
        }catch (TemplateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



}
