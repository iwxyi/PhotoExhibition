#!/usr/bin/env node

// 简单的开发服务器启动脚本
import { spawn } from 'child_process';

console.log('🚀 启动PhotoExhibition开发服务器...');

const host = process.env.HOST || '127.0.0.1';
const port = process.env.PORT || '3030';

// 启动vite开发服务器，默认绑定127.0.0.1，容器内可通过环境变量覆盖为0.0.0.0
const vite = spawn('node', ['./node_modules/vite/bin/vite.js', '--host', host, '--port', port], {
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
