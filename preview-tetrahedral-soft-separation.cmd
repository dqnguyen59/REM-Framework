@echo off
cd /d "%~dp0"
set "previewGeneration=3"
if not "%~1"=="" set "previewGeneration=%~1"
call mvn compile exec:exec "-Dexec.args=-classpath %%classpath remframework.geometry.discovery.GeometryDiscoveryVisualizerMain --model tetrahedral-network --target 10 --generation %previewGeneration% --max-nodes 1000 --layout soft-separation --layout-iterations 2000"
