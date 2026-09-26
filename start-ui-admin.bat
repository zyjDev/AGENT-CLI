@echo off
title Zhishu UI - 智枢（用户端 + 管理端）
cd /d "%~dp0zhishu-ui"
echo Frontend: http://localhost:5173
echo Login:    admin / 123456
start "" http://localhost:5173
npm run dev
