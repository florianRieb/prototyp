package com.gen;

import java.util.*;

public class Graph {
    private List<Vertex> vertices;
    private String rootModuel;
    private Map<String, Set<String>>  list4Vertices;
    private String[] alphabet = {"a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z"};
    private Set<String> validSet;


    public Graph(String rootModule, List<Edge<String,String>> constraints, Set<String> validSet){
        vertices = new LinkedList<>();
        list4Vertices = new HashMap<>();
        this.rootModuel = rootModule;
        this.validSet = validSet;

        for(Edge edge: constraints){
            connect(edge);
        }

        for(Map.Entry<String,Set<String>> entr: list4Vertices.entrySet()){
            vertices.add(new Vertex(entr.getKey(), entr.getValue()));
        }

    }//Constructor



    public void validModuleNames() throws Exception {
        for(Map.Entry<String,Set<String>> vertices:list4Vertices.entrySet()){

            if(!validSet.containsAll(vertices.getValue()))
                throw new Exception("One of the modules in the isomods.json from  module " + vertices.getKey() + " are not allowed for isolation ");


        }
    }
    private void connect(Edge<String,String> edge){

        //exist?
        if(!edge.getModule2Isolate().equals("") && !edge.getBaseModule().equals("")){


        if(list4Vertices.keySet().contains(edge.getBaseModule())){
            list4Vertices.get(edge.getBaseModule()).add(edge.getModule2Isolate());
        }else {
            list4Vertices.put(edge.getBaseModule(), new HashSet<>(Arrays.asList(edge.getModule2Isolate())));
        }

        if(list4Vertices.keySet().contains(edge.getModule2Isolate())){
            list4Vertices.get(edge.getModule2Isolate()).add(edge.getBaseModule());
        }else {
            list4Vertices.put(edge.getModule2Isolate(), new HashSet<>(Arrays.asList(edge.getBaseModule())));
        }

        }
    }

    public Map<String,String> colourVertices(){
        int chromaticNum = -1;
        Collections.sort(vertices, new VertexComparator()); // arrange vertices in order of descending valence

        Map<String, String> vertex_color_index = new HashMap<String, String>(); //create Map<Vertex, Color>
        int counter = 0;
        for (int i = 0; i < vertices.size(); i++){

            //ist Knoten bereits gefärbt?
            if ((vertex_color_index.containsKey(vertices.get(i).moduleName))){

                continue;
            }
            else{
                //ordne ersten Knoten der aktiven Farbe zu
                vertex_color_index.put(vertices.get(i).moduleName, "ENV" + alphabet[counter]);
                for (int j = i+1; j < vertices.size(); j++){
                    if (!(vertices.get(i).neighbors.contains(vertices.get(j).moduleName)) && !(vertex_color_index.containsKey(vertices.get(j).moduleName))){
                        vertex_color_index.put(vertices.get(j).moduleName, "ENV" + alphabet[counter]);
                        vertices.get(i).neighbors.addAll(vertices.get(j).neighbors);
                    }
                    else{
                        continue;
                    }
                }
                counter++;
            }

            chromaticNum = counter;
        }
        System.out.println("chromatic number of environments: " + chromaticNum);
        return vertex_color_index;

    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Vertex v: vertices){
            result.append(v.moduleName + " has the following neigbors: " + v.neighbors.toString() + "\n");

        }
        return result.toString();
    }

    private static class Vertex{
        private String moduleName;
        private Set<String> neighbors;

        public Vertex(String node, Set<String> neighbors){
            this.moduleName = node;
            this.neighbors = neighbors;
        }

    }



    class VertexComparator implements Comparator<Vertex> {

        public int compare(Vertex a, Vertex b) {
            if(a.equals(rootModuel)) return 1;

            return a.neighbors.size() < b.neighbors.size() ? 1 : a.neighbors.size() == b.neighbors.size() ? 0 : -1;
        }

    }
}
