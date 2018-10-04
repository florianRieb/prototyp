<#list clientModules as client>

find ${projDir}/generatedFiles/java/${client} -name "*.java" > client_source.txt
javac -d ${projDir}/generatedFiles/classes/${client} --module-path ${projDir}/mlib/  @client_source.txt
jar cf ${projDir}/generatedFiles/mods/client/${client}.jar -C ${projDir}/generatedFiles/classes/${client}/ .
</#list>

<#list envModules as env>
find ${projDir}/generatedFiles/java/${env} -name "*.java" > server_source.txt
javac -d ${projDir}/generatedFiles/classes/${env} --module-path ${projDir}/mlib/ @server_source.txt
jar cf ${projDir}/generatedFiles/mods/${env}.jar -C ${projDir}/generatedFiles/classes/${env}/ .
</#list>

rm client_source.txt
rm server_source.txt