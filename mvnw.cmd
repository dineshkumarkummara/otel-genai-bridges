@ECHO OFF
SETLOCAL

set WRAPPER_JAR=%~dp0\.mvn\wrapper\maven-wrapper.jar

if exist "%JAVA_HOME%\bin\java.exe" (
  set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
) else (
  for %%j in (java.exe) do set JAVA_EXE="%%~$PATH:j"
)

if not exist %JAVA_EXE% (
  echo ERROR: Java executable not found. Please set JAVA_HOME.
  exit /b 1
)

"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory=%~dp0 -classpath %WRAPPER_JAR% org.apache.maven.wrapper.MavenWrapperMain %*
ENDLOCAL
