@echo off
setlocal
set JAR_PATH=%~dp0dist\MissionTelemetryDashboard.jar
if not exist "%JAR_PATH%" (
  echo Build the project first so the jar exists:
  echo   Open in NetBeans and click Clean and Build.
  pause
  exit /b 1
)
java -jar "%JAR_PATH%"
