const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

// 保持对window对象的全局引用，如果不这么做的话，当JavaScript对象被
// 垃圾回收时，window对象将会自动的关闭
let mainWindow;
let backendProcess = null;

function createWindow() {
  // 创建浏览器窗口
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 1000,
    minHeight: 700,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      enableRemoteModule: false,
      preload: path.join(__dirname, 'preload.js')
    },
    icon: path.join(__dirname, 'dist/favicon.ico'), // 设置应用图标
    titleBarStyle: 'default',
    show: false // 先隐藏，等内容加载完再显示
  });

  // 加载应用的index.html
  const isDev = process.env.NODE_ENV === 'development';
  if (isDev) {
    mainWindow.loadURL('http://localhost:3030');
    mainWindow.webContents.openDevTools();
  } else {
    // 在生产环境中，从asar文件中加载
    const indexPath = path.join(process.resourcesPath, 'app.asar', 'dist', 'index.html');
    mainWindow.loadFile(indexPath);
  }

  // 当窗口被关闭，这个事件会被触发
  mainWindow.on('closed', () => {
    mainWindow = null;
    // 关闭后端进程
    if (backendProcess) {
      backendProcess.kill();
      backendProcess = null;
    }
  });

  // 窗口准备好显示时显示
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    // 启动后端服务
    startBackend();
  });
}

// 启动后端服务
function startBackend() {
  const isDev = process.env.NODE_ENV === 'development';
  const backendPath = isDev
    ? path.join(__dirname, '../backend/target/photo-exhibition-backend-1.0.0.jar')
    : path.join(path.dirname(process.resourcesPath), 'backend/photo-exhibition-backend-1.0.0.jar');

  if (fs.existsSync(backendPath)) {
    console.log('Starting backend server...');

    // 设置JAVA_HOME环境变量（如果需要）
    const env = { ...process.env };
    if (process.platform === 'win32') {
      env.JAVA_HOME = path.join(process.resourcesPath, 'jre');
    }

    backendProcess = spawn('java', ['-jar', backendPath], {
      cwd: isDev ? path.join(__dirname, '../backend') : path.dirname(process.resourcesPath),
      env: env,
      stdio: ['pipe', 'pipe', 'pipe']
    });

    backendProcess.stdout.on('data', (data) => {
      console.log(`Backend: ${data}`);
    });

    backendProcess.stderr.on('data', (data) => {
      console.error(`Backend Error: ${data}`);
    });

    backendProcess.on('close', (code) => {
      console.log(`Backend process exited with code ${code}`);
    });

    // 等待后端启动
    setTimeout(() => {
      if (mainWindow) {
        mainWindow.webContents.send('backend-ready');
      }
    }, 5000);
  } else {
    console.error('Backend JAR file not found:', backendPath);
    dialog.showErrorBox('启动错误', '后端服务文件未找到，请重新安装应用。');
  }
}

// Electron 会在初始化后并准备创建浏览器窗口时，调用这个函数
app.whenReady().then(createWindow);

// 当全部窗口关闭时退出
app.on('window-all-closed', () => {
  // 在 macOS 上，除非用户用 Cmd + Q 确定地退出，
  // 否则绝大部分应用及其菜单栏会保持激活
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  // 在 macOS 上，当单击 dock 图标并且没有其他窗口打开时，
  // 通常在应用中重新创建一个窗口
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

// IPC 处理
ipcMain.handle('select-directory', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory']
  });
  return result;
});

ipcMain.handle('open-external', async (event, url) => {
  shell.openExternal(url);
});

ipcMain.handle('get-app-path', () => {
  return app.getAppPath();
});

ipcMain.handle('get-platform', () => {
  return process.platform;
});
