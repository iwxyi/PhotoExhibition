package com.photoexhibition.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * ONNX Runtime 诊断工具
 * 用于详细诊断 ONNX Runtime 初始化失败的原因
 */
@Slf4j
@Component
public class ONNXDiagnosticUtil {

    /**
     * 执行完整的系统诊断
     */
    public void performFullDiagnostic() {
        log.info("=== ONNX Runtime 完整诊断开始 ===");

        try {
            checkJavaVersion();
            checkOperatingSystem();
            checkONNXRuntimeJAR();
            checkNativeLibraries();
            checkFilePermissions();
            attemptONNXInitialization();

        } catch (Exception e) {
            log.error("诊断过程中发生错误: ", e);
        }

        log.info("=== ONNX Runtime 完整诊断结束 ===");
    }

    /**
     * 检查Java版本
     */
    private void checkJavaVersion() {
        log.info("--- Java版本检查 ---");
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String jvmName = System.getProperty("java.vm.name");

        log.info("Java版本: {}", javaVersion);
        log.info("Java供应商: {}", javaVendor);
        log.info("JVM名称: {}", jvmName);

        // 检查Java版本是否支持
        if (javaVersion.startsWith("1.") && Integer.parseInt(javaVersion.split("\\.")[1]) < 11) {
            log.error("❌ Java版本过低，ONNX Runtime需要Java 11或更高版本");
        } else {
            log.info("✅ Java版本兼容");
        }
    }

    /**
     * 检查操作系统信息
     */
    private void checkOperatingSystem() {
        log.info("--- 操作系统检查 ---");
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");

        log.info("操作系统: {} {}", osName, osVersion);
        log.info("系统架构: {}", osArch);

        // 检查是否为支持的操作系统
        if (osName.toLowerCase().contains("windows")) {
            log.info("检测到Windows操作系统");
        } else if (osName.toLowerCase().contains("linux")) {
            log.info("检测到Linux操作系统");
        } else if (osName.toLowerCase().contains("mac")) {
            log.info("检测到macOS操作系统");
        } else {
            log.warn("⚠️ 检测到未知操作系统: {}", osName);
        }
    }

    /**
     * 检查ONNX Runtime JAR文件
     */
    private void checkONNXRuntimeJAR() {
        log.info("--- ONNX Runtime JAR检查 ---");

        try {
            Class<?> clazz = Class.forName("ai.onnxruntime.OrtEnvironment");
            log.info("✅ ONNX Runtime类找到: {}", clazz.getName());

            // 获取JAR文件位置
            URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
            log.info("JAR文件位置: {}", location);

            // 检查版本
            String version = clazz.getPackage().getImplementationVersion();
            if (version != null) {
                log.info("ONNX Runtime版本: {}", version);
            } else {
                log.warn("⚠️ 无法确定ONNX Runtime版本");
            }

        } catch (ClassNotFoundException e) {
            log.error("❌ ONNX Runtime类未找到: {}", e.getMessage());
            log.error("请确保Maven依赖正确配置: com.microsoft.onnxruntime:onnxruntime");
        } catch (Exception e) {
            log.error("❌ 检查ONNX Runtime JAR时出错: ", e);
        }
    }

    /**
     * 检查本地库
     */
    private void checkNativeLibraries() {
        log.info("--- 本地库检查 ---");

        String javaLibPath = System.getProperty("java.library.path");
        log.info("Java库路径: {}", javaLibPath);

        if (javaLibPath != null) {
            String[] paths = javaLibPath.split(System.getProperty("path.separator"));
            log.info("库路径包含 {} 个目录", paths.length);

            for (String path : paths) {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory()) {
                    String[] files = dir.list((d, name) -> name.contains("onnx") || name.contains("ort"));
                    if (files != null && files.length > 0) {
                        log.info("在 {} 找到ONNX相关库文件: {}", path, String.join(", ", files));
                    }
                }
            }
        }

        // 检查临时目录权限
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            if (tempDir.canWrite()) {
                log.info("✅ 临时目录可写: {}", tempDir.getAbsolutePath());
            } else {
                log.error("❌ 临时目录不可写: {}", tempDir.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("❌ 检查临时目录权限时出错: ", e);
        }
    }

    /**
     * 检查文件权限
     */
    private void checkFilePermissions() {
        log.info("--- 文件权限检查 ---");

        // 检查当前工作目录
        File currentDir = new File(System.getProperty("user.dir"));
        log.info("当前工作目录: {}", currentDir.getAbsolutePath());
        log.info("工作目录可读: {}, 可写: {}", currentDir.canRead(), currentDir.canWrite());

        // 检查用户主目录
        File homeDir = new File(System.getProperty("user.home"));
        log.info("用户主目录: {}", homeDir.getAbsolutePath());
        log.info("主目录可读: {}, 可写: {}", homeDir.canRead(), homeDir.canWrite());
    }

    /**
     * 尝试ONNX Runtime初始化
     */
    private void attemptONNXInitialization() {
        log.info("--- ONNX Runtime初始化测试 ---");

        try {
            log.info("尝试创建OrtEnvironment...");
            Class<?> envClass = Class.forName("ai.onnxruntime.OrtEnvironment");
            Object env = envClass.getMethod("getEnvironment").invoke(null);
            log.info("✅ ONNX Runtime初始化成功");

            // 尝试创建会话选项
            Class<?> optsClass = Class.forName("ai.onnxruntime.OrtSession$SessionOptions");
            Object opts = optsClass.getConstructor().newInstance();
            log.info("✅ SessionOptions创建成功");

        } catch (NoClassDefFoundError e) {
            log.error("❌ 类初始化失败: {}", e.getMessage());
            log.error("这通常表示缺少JNI依赖库");
            printTroubleshootingGuide();
        } catch (UnsatisfiedLinkError e) {
            log.error("❌ 链接错误: {}", e.getMessage());
            log.error("这表示本地库无法加载");
            printTroubleshootingGuide();
        } catch (Exception e) {
            log.error("❌ 初始化失败: ", e);
        }
    }

    /**
     * 打印故障排除指南
     */
    private void printTroubleshootingGuide() {
        log.info("--- 故障排除指南 ---");

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("windows")) {
            log.info("Windows解决方案:");
            log.info("1. 安装 Visual C++ Redistributable 2015-2022 (x64)");
            log.info("   下载: https://aka.ms/vs/17/release/vc_redist.x64.exe");
            log.info("2. 如果是Windows Server，确保安装Desktop Experience");
            log.info("3. 检查防火墙是否阻止了DLL加载");
            log.info("4. 尝试以管理员身份运行");
        } else if (os.contains("linux")) {
            log.info("Linux解决方案:");
            log.info("1. 安装系统依赖:");
            log.info("   Ubuntu/Debian: sudo apt-get install libgomp1 libatlas-base-dev libopenblas-dev");
            log.info("   CentOS/RHEL: sudo yum install libgomp atlas-devel openblas-devel");
            log.info("2. 检查glibc版本 (需要2.17+)");
            log.info("3. 确保LD_LIBRARY_PATH包含库路径");
        } else if (os.contains("mac")) {
            log.info("macOS解决方案:");
            log.info("1. 确保Xcode Command Line Tools已安装:");
            log.info("   xcode-select --install");
            log.info("2. 检查macOS版本兼容性");
        }

        log.info("通用解决方案:");
        log.info("1. 确保Java版本 >= 11");
        log.info("2. 检查磁盘空间是否足够");
        log.info("3. 尝试清理临时文件");
        log.info("4. 检查是否有杀毒软件干扰");
        log.info("5. 如果使用Docker，确保正确配置了权限");
    }
}
