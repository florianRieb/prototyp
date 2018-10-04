package com.gen;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import javax.swing.text.html.Option;
import java.io.*;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class Main {
    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());
    static Map<String,Path> mlibSourcees = new HashMap<>();
    static String arg1 = null;
    static String projectDir  = null;

    public static void main(String... args) throws IOException, ClassNotFoundException {
        //############################################################
        //Init Block
        if(args.length==2){
            projectDir = args[0];
            arg1 = args[1];

        }else{
            LOGGER.warning("Benötigte Parameter Fehlen arg0 = workDir und arg1 = <modulename>/<package>.Main");
            System.exit(0);
        }

        /**
         * Benötigt als Eingabe 2 Strings
         * arg[1] app/com.main.Main - Name des Moduls mit Packet und Klassenbezeichnung der Main
         * arg[0] Verzeichnis indem das Projekt liegt
         * arg[2] OPTIONAL Ordner indem die Dependenzies liegen z.B. weitere Frameworks etc. als jar Dateien und Modul kompatibel
         */

        //Got project dir = user.dir
         String  args0 = System.getProperty("user.dir");
        // String arg1 = "app/com.main.Main";
         String workDir = projectDir+"generatedFiles"+File.separator;
         String rootModule = arg1.substring(0,arg1.indexOf("/"));
        TempGenerator temp = new TempGenerator();

        createWorkDirs(workDir);
// 1.) Kompelieren des Projektes, Module werden in /mlib abgelegt

        // hier werden die Module des Projektes mit Maven gepackt  -> mvn package -f <args0> -DoutputDirectory=/mlib
        RuntimeExecutor runExec = new RuntimeExecutor();
        runExec.mvnCompile(projectDir);



        //init Collections

        Map<String,Map<String,Path>>    environments =     new HashMap<>();
        Set<String>                     allowedModules =   new HashSet<>();
        Map<String,String>              dockerLocations =  new HashMap<>();
        Set<String>                     clientModules =    new HashSet<>();
        Set<String>                     envModules =       new HashSet<>();
        List<Edge<String,String>>       edges=             new LinkedList<>();
        Map<String,Set<String>>         ENV =              new HashMap<>();
        Set<String>                     modules4Isolation =new HashSet<>();
        Set<String>                     serviceModules =   new HashSet<>();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(Feature.AUTO_CLOSE_SOURCE, true);

        TypeFactory typeFactory = mapper.getTypeFactory();
        CollectionType collectionType = typeFactory.constructCollectionType(
                List.class, Edge.class);

        ModuleFinder finder = ModuleFinder.of(Paths.get(projectDir+ "/mlib"));
        //Speicher Modulename und Path aller Module in mlib
        finder.findAll().stream().forEach((ModuleReference ref) -> {
            mlibSourcees.put(ref.descriptor().name(),Paths.get(ref.location().get().getPath()));
        });


//2.) Durchsuche die Service Module nach isomod.json
        //Suche nur nach Service-Modulen
        finder.findAll().stream().filter((ModuleReference ref) -> ref.descriptor().provides().size()>0)
                .forEach((ModuleReference modRef) -> {

                    serviceModules.add(modRef.descriptor().name());
                    Optional<URI> ismodUri = null;
                    try {
                        ismodUri = modRef.open().find("isomod.json");
                        System.out.println(modRef.descriptor().name() + " " + ismodUri.isPresent());

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if(ismodUri.isPresent()){
                        allowedModules.add(modRef.descriptor().name());

                      }

                });


        //Wenn kein Modul isoliert werden soll
        if(allowedModules.size()==0){
            Set<String> applicationModules = Set.of(rootModule);
            ENV.put("ENVa",applicationModules);


        }else{



        //Laden der Module und generieren des Source Codes

        ModuleLayer bootLayer = ModuleLayer.boot();
        //System.out.println(allowedModules);
        //bootLayer.modules().stream().forEach(System.out::println);
        Configuration config = bootLayer.configuration().resolve(finder,ModuleFinder.of(), allowedModules);

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        ModuleLayer newLayer = bootLayer.defineModulesWithOneLoader(config,scl);

        String packageName;
        String clientDir = workDir + File.separator + "java" + File.separator;
        String serverDir = workDir + File.separator + "java" + File.separator;

//3.) lese die Json Files aus und erstelle Edge Liste

        newLayer.modules().stream().filter((Module m) -> allowedModules.contains(m.getDescriptor().name()))
                .forEach((Module m) -> {
                    try {
                        //InputStream inputStream = m.getClassLoader().getResourceAsStream("isomod.json");

                        InputStream inputStream = m.getResourceAsStream("isomod.json");
                        List<Edge<String,String>> list = new ArrayList<>();

                            list =  mapper.readValue(inputStream, collectionType );
                            edges.addAll(list);


                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

// 4.) Berechne min Anzahl an Env und die Zuordnung
        allowedModules.add(rootModule);
        Graph graph = new Graph(rootModule,edges,allowedModules);
        try {
            graph.validModuleNames();
            Map<String,String> chromaticEnv=  graph.colourVertices();
            //System.out.println(chromaticEnv);

            ENV = swap(chromaticEnv);
            System.out.println(ENV);

        } catch (Exception e) {
            e.printStackTrace();
        }


//5.1) Client Stub Module anlegen für alle Services die nicht ENVa zurgeordnet wurden
        for(Map.Entry<String,Set<String>> entry: ENV.entrySet()){
            //Für alle Services
            if(!entry.getValue().contains(rootModule)){
                Set<Class> serviceImplClasse = new HashSet<>();
                Set<Module> mods4Generation = new HashSet<>();

                for(String modulName:entry.getValue()){

                    Optional<Module> opModule = newLayer.findModule(modulName);

                    if(opModule.isPresent()) {
                        Module module = opModule.get();
                        mods4Generation.add(module);
                        //lade Serviceimplementierung
                        Optional<Class> clazz = loadProviderClass(module);
                        if(clazz.isPresent()){
                            String clientModName =  genClientModule(module,clazz.get(),clientDir);
                            serviceImplClasse.add(clazz.get());

                            //Sammlungen für die Erstellunge des Dockerfiles
                            clientModules.add(clientModName);
                            serviceModules.remove(modulName);
                            serviceModules.add(clientModName);
                        }
                    }//isPresent


                }//forModulesinSet
//5.2) Generieren des Server Modules
                if(serviceImplClasse.size()>0 && mods4Generation.size()>0){
                    if(serviceImplClasse.size() != mods4Generation.size())
                        LOGGER.warning("Es konnten nicht zu jedem Service-Modul eine Impl-Klasse gefunden werden");



                 String isolatedModuleName = entry.getKey();
                 genIsolatedModule(isolatedModuleName,mods4Generation,serviceImplClasse,serverDir+entry.getKey());
                 envModules.add(isolatedModuleName);


                }


            }//if!=root

        }//for ENV

        //Erstelle Compile-skript
        temp.skript(projectDir,clientModules,envModules);

        // libmodule wird für Kompilierung der generierten Module benötigt,deshalb kopiere in libmodule aus resources/mods -> projectDir/mlib
        String directoryString = "/proxygen/src/main/resources/mods/";
        directoryString.replace("/",File.separator);
        copyFolder(Paths.get(System.getProperty("user.dir")+directoryString+File.separator+"libmodule-1.0-SNAPSHOT.jar").toFile(),Paths.get(projectDir+File.separator+"mlib"+File.separator+"libmodule.jar").toFile());

        //Skript ausführen

        if(!runExec.execSkript()){
            LOGGER.severe("Compilation failed");
            System.exit(1);

        }


        }//else -> kein Modul soll isoliert werden


       //Benötigte Module zu der jeweiligen ENV zuordnen
        for(Map.Entry<String,Set<String>> entry: ENV.entrySet()){
            Map<String,Path> dependencies = new HashMap<>();
            environments.put(entry.getKey(),new HashMap<>());

            for(String moduleName:entry.getValue()){

                Optional<ModuleReference> modRef= finder.find(moduleName);
                if(modRef.isPresent())
                    dependencies = readRequiredModules(modRef.get().descriptor());

            }


            if(entry.getKey().equals("ENVa")){
                dependencies.put(rootModule, mlibSourcees.get(rootModule));

               List<String> services4App = serviceModules.stream().filter((String name)-> mlibSourcees.keySet().contains(name)).collect(Collectors.toList());
               for(String name:services4App){
                   dependencies.put(name,mlibSourcees.get(name));
                   Optional<ModuleReference> modRef= finder.find(name);
                   if(modRef.isPresent()){
                       dependencies.putAll(readRequiredModules(modRef.get().descriptor()));
                   }
               }

            }else{

                Path sourcePath =  Paths.get(workDir +File.separator + "mods" + File.separator + entry.getKey()+".jar");
                if(!sourcePath.toFile().exists())
                    System.out.println("Env File nicht gefunden !!!!!!");
                dependencies.put(entry.getKey()+"_module",sourcePath);
            }


            if(dependencies.keySet().size()>0){
                environments.get(entry.getKey()).putAll(dependencies);
            }
            else  {
            LOGGER.warning("Module "+ rootModule + " can't be found in /mlib Dir");
        }


        }


        //erstelle ein Verzeichnis  je Env und kopiere alle Jars rein
       createEnvDirs(workDir,environments);


       dockerLocations = genDockerfiles(environments.keySet(),workDir+"environments",arg1,  serviceModules);


       temp.createYAML(workDir,dockerLocations,rootModule);



    }//main()

    public static String  genClientModule(Module module, Class serviceImpl, String workDir) throws IOException {
        String clientDir = workDir +module.getName()+"_client";

        PoetGen poetGen = new PoetGen();
         poetGen.genClientClass(module.getName(),serviceImpl,clientDir);

        TempGenerator moduleInfoGen = new TempGenerator();
        String modulName = moduleInfoGen.cloneModuleInfo(module.getDescriptor(),clientDir,"");
        return modulName;



    }

    public static void genServerModule(Module module, Class serviceImpl, String workDir) throws IOException {
        String serverDir = workDir + File.separator  + "_server";
        String packageName = serviceImpl.getPackageName().replace(".",File.separator);
        String fileName = serviceImpl.getName().replace(".",File.separator);
        //Erstelle Verzeichnis
        Files.createDirectories(Paths.get(serverDir+ File.separator+packageName,File.separator));

        //Kopiere ServiceImpl Klasse
        Path serviceImplPath  =  Paths.get(System.getProperty("user.dir")+ File.separator+module.getName()+File.separator+"src/main/java/"+fileName+".java");
        Path serverModulePath = Paths.get(serverDir+File.separator+ fileName+".java");
        Files.copy(serviceImplPath,serverModulePath, StandardCopyOption.REPLACE_EXISTING);

        PoetGen poetGen = new PoetGen();
        //poetGen.genServerClass(serviceImpl,serverDir);
        //Erstelle module-info mit qualified export zu proxygen - So hat ZMQServer Zugriff auf die ServiceImpl.class
        TempGenerator moduleInfoGen = new TempGenerator();
        moduleInfoGen.cloneModuleInfo(module.getDescriptor(),serverDir,serviceImpl.getPackageName());

    }

    public static void genIsolatedModule(String moduleName, Set<Module> serviceModules, Set<Class> serviceImpl, String workDir) throws IOException {

        //Kopiere Service Impl. Klassen
        for(Class c:serviceImpl){
            String fileName = c.getName().replace(".",File.separator);
            Path serviceImplPath  =  Paths.get(Main.projectDir + File.separator+c.getModule().getName()+File.separator+"src/main/java/"+fileName+".java");

            Path envPath = Paths.get(workDir+File.separator+ fileName+".java");
            //Files.copy(serviceImplPath,envPath , StandardCopyOption.REPLACE_EXISTING);
            copyFolder(serviceImplPath.toFile(),envPath.toFile());

        }

        //Erstelle Server Klasse
        PoetGen poetGen = new PoetGen();
        poetGen.genServerClass(serviceImpl,workDir);

        TempGenerator templator = new TempGenerator();
        templator.createServerModuleInfo(moduleName,serviceModules,workDir,serviceImpl);


    }

    private static void createWorkDirs(String workDir){

        //dele generatedFiles if exists
        File genFiles = Paths.get(workDir).toFile();
        if(genFiles.exists()){
            genFiles.deleteOnExit();
        }


        String[] workdirs = new String[6];
        workdirs[0] = workDir;
        workdirs[1] = workDir+"java";
        workdirs[2] = workDir+"classes";
        workdirs[3] = workDir+ "mods";
        workdirs[4] =  workDir+ "mods" +File.separator+"client";
        workdirs[5] = workDir+"environments";
        for(int i = 0; i<workdirs.length ;i++){
            if(!new File(workdirs[i]).exists()){
                new File(workdirs[i]).mkdir();
                System.out.println("Directory created :: " + workdirs[i]);

            }
        }



    }

    private static void copyFolder(File sourceFolder, File destinationFolder) throws IOException
    {
        //Check if sourceFolder is a directory or file
        //If sourceFolder is file; then copy the file directly to new location
        if (sourceFolder.isDirectory())
        {
            //Verify if destinationFolder is already present; If not then create it
            if (!destinationFolder.exists())
            {
                destinationFolder.mkdir();
                //System.out.println("Directory created :: " + destinationFolder);
            }

            //Get all files from source directory
            String files[] = sourceFolder.list();

            //Iterate over all files and copy them to destinationFolder one by one
            for (String file : files)
            {
                File srcFile = new File(sourceFolder, file);
                File destFile = new File(destinationFolder, file);

                //Recursive function call
                copyFolder(srcFile, destFile);
            }
        }
        else
        {
            if(!destinationFolder.exists()){
                destinationFolder.mkdirs();
                //System.out.println("Directory created :: " + destinationFolder);
            }

            //Copy the file content from one place to another
            Files.copy(sourceFolder.toPath(), destinationFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            //System.out.println("File copied :: " + destinationFolder);
        }
    }

    private static Map<String,Path>  readRequiredModules(ModuleDescriptor modDesc){
        Map<String,Path> serviceDep= new HashMap<>();
        Set<ModuleDescriptor.Requires> requires =  modDesc.requires().stream()
                .filter((ModuleDescriptor.Requires req) -> !req.name().startsWith("java."))
                .filter((ModuleDescriptor.Requires req) -> !req.name().startsWith("javax"))
                .collect(Collectors.toSet());
        for(ModuleDescriptor.Requires r: requires){
            if (mlibSourcees.get(r.name()) != null){
                serviceDep.put(r.name(),mlibSourcees.get(r.name()));

            }else
            {
                //sobald eine Abhängigkeit nicht gefunden wurde ist der Server nicht lauffähig
                LOGGER.warning("Von " +modDesc.name() +" benötigtes Module " + r.name() + "nicht im Verzeichnis mlib gefunden");
                return null;
            }

        }
        return serviceDep;

    }

    private static void createEnvDirs(String workDir,Map<String,Map<String,Path>> environments ){
        Map<String,Path> tempEnv;
        File modSource = new File(System.getProperty("user.dir")+"/proxygen/src/main/resources/mods/");
        //workdir = projectDir + /generatedFiles;
        String envDir = workDir + File.separator +"environments" + File.separator;
        //rootModule is the main application
        Map<String,Map<String,Path>> envis = environments;
        //env-0 should always include the main application
        String baseEnv = "ENVa";


        tempEnv = envis.get(baseEnv);

        for(Map.Entry<String,Path> entry: tempEnv.entrySet()){
            //generatedFiles/environments/env0/<...>.jar
                //dependencies
            Path destPath =  Paths.get(envDir +baseEnv + File.separator + entry.getKey()+".jar");
                //helper
            Path desPath2 = Paths.get(envDir + baseEnv );
                //clients
            Path clientsource = Paths.get(workDir + File.separator + "mods" + File.separator + "client");
            try {
                copyFolder(entry.getValue().toFile(),destPath.toFile());
                copyFolder(modSource,desPath2.toFile());
                copyFolder(clientsource.toFile(),desPath2.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for(String key: envis.keySet()){
            if(!key.equals(baseEnv)){


              for(Map.Entry<String,Path> entry: envis.get(key).entrySet()){
                  Path destPath =  Paths.get(envDir + key +File.separator + entry.getKey()+".jar");
                  Path desPath2 = Paths.get(envDir + key);
                  try {
                      copyFolder(entry.getValue().toFile(),destPath.toFile());
                      copyFolder(modSource,desPath2.toFile());
                  } catch (IOException e) {
                      e.printStackTrace();
                  }
              }
            }
        }




    }

    private static Map<String,String>  genDockerfiles (Set<String> moduleName, String envDir, String rootModuleArgs, Set<String> services){

        //Set<String> testSet = new HashSet<>(Set.of("app","service.a_server","service.b","servicec"));


        TempGenerator templateGen = new TempGenerator();
        templateGen.cfg.setInterpolationSyntax(templateGen.cfg.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
        Map<String,String> fileLocations = new HashMap<>();


            try {
                Files.list(Paths.get(envDir)).forEach((Path path) -> {
                    //Für jedes Dir envX
                    ModuleFinder finder = ModuleFinder.of(path);
                   // finder.findAll().stream().filter((ModuleReference ref) -> moduleName.contains(ref.descriptor().name())).forEach((ModuleReference ref) -> {

                        try{
                            if(path.endsWith("ENVa")){
                                //App
                                String appName = rootModuleArgs.substring(0,rootModuleArgs.indexOf("/"));
                                String filePath =  templateGen.dockerMainAppFile(path.toString(),rootModuleArgs, services);
                                fileLocations.put(appName,filePath);

                            }else{

                                String filePath = templateGen.dockerServerFile(path.toString(),path.toString(),path.toFile().getName());
                                fileLocations.put(path.toFile().getName(),filePath);

                            }


                        }catch (IOException e) {
                            e.printStackTrace();
                        }


                   // });
                });
            } catch (IOException e1) {
                e1.printStackTrace();
            }


            return  fileLocations;

        }


    private static Optional<Class> loadProviderClass(Module module){
        // Only single class is possible
        Optional<Class> clazz;
        String className = module.getDescriptor().provides().stream().findFirst().get().providers().stream().findFirst().get().toString();
        ClassLoader cl = module.getClassLoader();
        try {

           clazz = Optional.ofNullable(cl.loadClass(className));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            clazz = Optional.ofNullable(null);
        }
        return clazz;

    }
    public static Map swap(Map<String, String> map){
        Map<String,Set<String>> returnMap = new HashMap<>();

        for(Map.Entry<String,String> entry:map.entrySet())
            if(returnMap.keySet().contains(entry.getValue())){
                returnMap.get(entry.getValue()).add(entry.getKey());
            }else{
                returnMap.put(entry.getValue(),new HashSet<String>(Set.of(entry.getKey())));
            }

        return  returnMap;


    }

}//class
