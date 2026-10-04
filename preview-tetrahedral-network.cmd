@echo off
cd /d "%~dp0"
if not "%~1"=="" goto generation
call mvn compile exec:exec "-Dexec.args=-classpath %%classpath remframework.geometry.discovery.GeometryDiscoveryVisualizerMain --model tetrahedral-builder --target 10 --max-nodes 1000"
goto end
:generation
call mvn compile exec:exec "-Dexec.args=-classpath %%classpath remframework.geometry.discovery.GeometryDiscoveryVisualizerMain --model tetrahedral-network --target 10 --generation %~1 --max-nodes 1000"
:end
