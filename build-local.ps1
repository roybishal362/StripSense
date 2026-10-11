$env:JAVA_HOME = 'E:\Andriod studio\jbr'
$env:GRADLE_USER_HOME = 'E:\ss-gradle'
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
    exit $LASTEXITCODE
} finally { Pop-Location }
