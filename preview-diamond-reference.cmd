@echo off
cd /d "%~dp0"
mvn exec:exec "-Dexec.args=-classpath %%classpath remframework.geometry.discovery.GeometryDiscoveryVisualizerMain --model diamond-reference --target 10"
