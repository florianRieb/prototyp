module ${moduleName} {
<#list requires as req>
requires ${req};
</#list>

<#list exports as ex>
exports ${ex};
</#list>

<#list provides as prov>
provides ${prov};
</#list>
}