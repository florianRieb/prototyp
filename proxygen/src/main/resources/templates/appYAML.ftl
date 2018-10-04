kind: Deployment
metadata:
    name: ${rootModuleName}
    labels:
        app: ${rootModuleName}
spec:
    replicas: 1
    selector:
        matchLabels:
            app: ${rootModuleName}
    template:
        metadata:
            labels:
                 app: ${rootModuleName}
        spec:
            containers:
<#list environments as env>

            - name: ${env}

              image: <use/imageName:tag>
              imagePullPolicy: Always
</#list>

