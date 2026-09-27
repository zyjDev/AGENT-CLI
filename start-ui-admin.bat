@echo off
title Zhishu UI - User Chat + Admin Console
cd /d "%~dp0zhishu-ui"

echo Frontend: http://localhost:5173

start "" http://localhost:5173
npm run dev
