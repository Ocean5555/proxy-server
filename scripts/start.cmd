CHCP 65001
@echo off  
set ANSICON=120x90
cd ..
set base_dir=%cd%

cd %base_dir%\main
for %%F in (*jar) do (
    set "jarName=%%F"
    goto :foundJar
)
:foundJar

set CONF_DIR=%base_dir%\conf
set LIB_DIR=%base_dir%\lib
set LOG_DIR=%base_dir%\logs
if not exist %LOG_DIR% (
    mkdir %LOG_DIR%
)
set STDOUT_FILE=%LOG_DIR%\stdout.log
set MAIN_JARS=%base_dir%\main\%jarName%
set JAVA_OPTS=-Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8
set JAVA_MEM_OPTS=-server -Xms800m -Xmx800m  -XX:MaxNewSize=256m

echo %CONF_DIR%:%LIB_DIR%:%MAIN_JARS%
rem start java -jar %JAVA_OPTS% %AVA_MEM_OPTS%  -Dloader.path=%CONF_DIR%,%LIB_DIR% %MAIN_JARS% > %STDOUT_FILE% 2>&1
java -jar %JAVA_OPTS% %AVA_MEM_OPTS%  -Dloader.path=%CONF_DIR%,%LIB_DIR% %MAIN_JARS%
