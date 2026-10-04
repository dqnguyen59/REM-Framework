@echo off
cd /d "%~dp0"
set "closureTarget=10"
if not "%~1"=="" set "closureTarget=%~1"
call mvn compile exec:exec "-Dexec.args=-classpath %%classpath remframework.geometry.discovery.GeometryDiscoveryVisualizerMain --model tetrahedral-closure --target %closureTarget%"
