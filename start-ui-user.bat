@echo off
title AI Agent Station - User
cd /d "%~dp0docs\dev-ops\nginx\html"
echo User UI: http://localhost:8080/index.html
start http://localhost:8080/index.html
python -m http.server 8080