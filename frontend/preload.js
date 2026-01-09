const { contextBridge, ipcRenderer } = require('electron');

// 通过 contextBridge 暴露安全的API
contextBridge.exposeInMainWorld('electronAPI', {
  // 文件选择
  selectDirectory: () => ipcRenderer.invoke('select-directory'),

  // 打开外部链接
  openExternal: (url) => ipcRenderer.invoke('open-external', url),

  // 获取应用路径
  getAppPath: () => ipcRenderer.invoke('get-app-path'),

  // 获取平台信息
  getPlatform: () => ipcRenderer.invoke('get-platform'),

  // 监听后端准备就绪事件
  onBackendReady: (callback) => {
    ipcRenderer.on('backend-ready', callback);
  },

  // 移除监听器
  removeAllListeners: (event) => {
    ipcRenderer.removeAllListeners(event);
  }
});
