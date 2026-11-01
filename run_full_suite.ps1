$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:PATH='C:\Program Files\Java\jdk-25.0.2\bin;' + $env:PATH
Set-Location 'C:\Users\mona mohamed\Downloads\Payagent-integration-20261025\repo\services\api'
.\mvnw -B test -DargLine='-Dnet.bytebuddy.experimental=true'
