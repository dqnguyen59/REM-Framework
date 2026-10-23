@echo off
cd /d "%~dp0"
call mvn exec:exec -Dexec.args="-XX:MaxRAMPercentage=70 -classpath %%classpath remframework.Main --diamond-mechanics"
