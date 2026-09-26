@echo off
title Zhishu UI - User Chat + Admin Console
cd /d "%~dp0zhishu-ui"

echo Frontend: http://localhost:5173
echo Login:    admin / 123456

start "" http://localhost:5173
npm run dev
