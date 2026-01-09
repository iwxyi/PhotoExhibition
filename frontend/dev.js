#!/usr/bin/env node

// 简单的开发服务器启动脚本
import { spawn } from 'child_process';

console.log('🚀 启动PhotoExhibition开发服务器...');

// 启动vite开发服务器
const vite = spawn('node', ['./node_modules/vite/bin/vite.js'], {
  stdio: 'inherit',
  cwd: process.cwd()
});

vite.on('close', (code) => {
  process.exit(code);
});

vite.on('error', (err) => {
  console.error('启动失败:', err);
  process.exit(1);
});
