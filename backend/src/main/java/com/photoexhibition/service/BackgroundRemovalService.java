package com.photoexhibition.service;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.List;

/**
 * 基于 ONNX 模型的背景移除服务
 * 使用 BriaAI RMBG 模型进行高质量背景移除
 * 
 * 模型特点：
 * - 纯本地运行，支持 CPU
 * - 专门针对人像优化，边缘处理细腻
 * - 支持批量处理
 * 
 * 使用场景：
 * - 人物抠图用于相册封面立体效果
 * - 生成透明背景人物图片
 */
@Slf4j
@Service
public class BackgroundRemovalService implements AutoCloseable {

    @Value("${background.removal.model-path:./models/briaai_rmbg.onnx}")
    private String modelPath;

    @Value("${background.removal.enabled:false}")
    private boolean enabled;

    @Value("${background.removal.cpu-threads:4}")
    private int cpuThreads;

    @Value("${background.removal.keep-size:1024}")
    private int keepSize;

    // 输出图片的最大尺寸（宽度或高度），用于优化输出文件大小
    @Value("${background.removal.output-max-size:800}")
    private int outputMaxSize;

    @Value("${background.removal.threshold-transparent:0.2}")
    private float thresholdTransparent;  // 低于此值设为完全透明

    @Value("${background.removal.threshold-solid:0.9}")
    private float thresholdSolid;  // 高于此值设为完全不透明

    // 腐蚀半径，用于去除边缘噪点
    @Value("${background.removal.erode-radius:0}")
    private int erodeRadius;

    // 高斯模糊半径，用于平滑边缘
    @Value("${background.removal.blur-radius:0}")
    private int blurRadius;

    // 并发处理线程数
    @Value("${background.removal.concurrent-tasks:2}")
    private int concurrentTasks;

    private OrtEnvironment env;
    private OrtSession session;
    private boolean modelLoaded = false;
    
    // 线程池用于并发处理
    private java.util.concurrent.ExecutorService processingExecutor;
    
    // 追踪正在处理的任务，避免重复处理同一图片
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.Future<?>> inProgressTasks = new java.util.concurrent.ConcurrentHashMap<>();

    // 预处理的均值和标准差（根据模型训练配置）
    private static final float[] MEAN = new float[]{0.5f, 0.5f, 0.5f};
    private static final float[] STD = new float[]{0.5f, 0.5f, 0.5f};

    // 服务启动时自动加载模型
    @PostConstruct
    public void init() {
        log.info("========== BackgroundRemovalService 初始化 ==========");
        log.info("enabled: {}", enabled);
        log.info("原始modelPath: {}", modelPath);
        log.info("cpuThreads: {}", cpuThreads);
        log.info("keepSize: {}", keepSize);
        log.info("outputMaxSize: {}", outputMaxSize);
        log.info("thresholdTransparent: {}", thresholdTransparent);
        log.info("thresholdSolid: {}", thresholdSolid);
        log.info("erodeRadius: {}", erodeRadius);
        log.info("blurRadius: {}", blurRadius);
        log.info("concurrentTasks: {}", concurrentTasks);
        
        // 初始化线程池
        processingExecutor = java.util.concurrent.Executors.newFixedThreadPool(concurrentTasks);
        log.info("背景移除线程池初始化完成，核心线程数: {}", concurrentTasks);
        
        // 尝试解析模型路径
        String resolvedPath = resolveModelPath(modelPath);
        log.info("解析后modelPath: {}", resolvedPath);
        
        // 检查模型文件是否存在
        File modelFile = new File(resolvedPath);
        if (!modelFile.exists()) {
            // 尝试从 classpath 查找
            log.warn("模型文件不存在: {}，尝试从 classpath 查找", resolvedPath);
        } else {
            log.info("模型文件存在，大小: {} bytes", modelFile.length());
        }
        
        if (enabled) {
            ensureModelLoaded();
        } else {
            log.warn("背景移除功能未启用 (enabled=false)");
        }
        log.info("========== BackgroundRemovalService 初始化完成 ==========");
    }

    /**
     * 解析模型路径，支持相对路径和绝对路径
     */
    private String resolveModelPath(String path) {
        File file = new File(path);
        if (file.exists()) {
            return path;
        }
        
        // 尝试相对于项目根目录
        String userDir = System.getProperty("user.dir");
        File fromUserDir = new File(userDir, path);
        if (fromUserDir.exists()) {
            return fromUserDir.getAbsolutePath();
        }
        
        // 尝试相对于 backend 目录
        File backendDir = new File(userDir, "backend");
        File fromBackend = new File(backendDir, path);
        if (fromBackend.exists()) {
            return fromBackend.getAbsolutePath();
        }
        
        return path;
    }

    /**
     * 初始化模型
     */
    public void ensureModelLoaded() {
        if (!enabled) {
            log.debug("背景移除功能已禁用");
            return;
        }

        if (modelLoaded && session != null) {
            return;
        }

        try {
            // 解析模型路径
            String resolvedPath = resolveModelPath(modelPath);
            File modelFile = new File(resolvedPath);
            
            // 如果解析后的路径不行，尝试原路径
            if (!modelFile.exists()) {
                modelFile = new File(modelPath);
            }
            
            if (!modelFile.exists()) {
                log.warn("背景移除模型文件不存在: {} (解析后: {})，功能将不可用", modelPath, resolvedPath);
                return;
            }

            log.info("加载背景移除模型，路径: {}", modelFile.getAbsolutePath());

            // 检查 ONNX Runtime 是否可用
            try {
                Class.forName("ai.onnxruntime.OrtEnvironment");
            } catch (ClassNotFoundException e) {
                log.warn("ONNX Runtime 类不存在，背景移除功能将被禁用");
                return;
            }

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            
            // 设置 CPU 线程数
            opts.setIntraOpNumThreads(cpuThreads);
            opts.setInterOpNumThreads(cpuThreads);
            
            // 优化选项
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            session = env.createSession(modelFile.getAbsolutePath(), opts);
            
            // 获取输入输出信息用于验证
            var inputInfo = session.getInputInfo();
            var outputInfo = session.getOutputInfo();
            
            log.info("背景移除模型加载成功: {}", modelPath);
            log.info("输入节点: {}", inputInfo.keySet());
            log.info("输出节点: {}", outputInfo.keySet());
            
            modelLoaded = true;
        } catch (NoClassDefFoundError e) {
            log.warn("ONNX Runtime 类初始化失败: {}", e.getMessage());
            modelLoaded = false;
        } catch (UnsatisfiedLinkError e) {
            log.warn("ONNX Runtime 本地库缺失: {}", e.getMessage());
            logSystemInfo();
            modelLoaded = false;
        } catch (Exception e) {
            log.warn("加载背景移除模型失败: {}", e.getMessage());
            modelLoaded = false;
        }
    }

    /**
     * 移除图片背景，返回带透明通道的图片
     * 
     * @param inputImage 输入图片文件
     * @param outputFile 输出 PNG 文件（支持透明背景）
     * @return 是否成功
     */
    public boolean removeBackground(File inputImage, File outputFile) {
        return removeBackground(inputImage, outputFile, outputMaxSize);
    }

    /**
     * 带人脸区域优化的背景移除
     * 
     * @param inputImage 输入图片
     * @param outputFile 输出 PNG 文件
     * @param faceRegions 人脸区域列表（用于优化掩码）
     * @return 是否成功
     */
    public boolean removeBackground(File inputImage, File outputFile, java.util.List<java.awt.Rectangle> faceRegions) {
        return removeBackground(inputImage, outputFile, outputMaxSize, faceRegions);
    }

    /**
     * 移除图片背景，返回带透明通道的图片（支持自定义输出尺寸）
     * 
     * @param inputImage 输入图片文件
     * @param outputFile 输出 PNG 文件（支持透明背景）
     * @param maxOutputSize 输出图片的最大尺寸（宽度或高度）
     * @return 是否成功
     */
    public boolean removeBackground(File inputImage, File outputFile, int maxOutputSize) {
        if (!enabled) {
            log.debug("背景移除功能已禁用");
            return false;
        }

        ensureModelLoaded();
        if (!modelLoaded || session == null) {
            log.warn("模型未加载，跳过背景移除: {}", inputImage.getName());
            return false;
        }

        long startTime = System.currentTimeMillis();
        
        try {
            // 读取图片
            BufferedImage original = ImageIO.read(inputImage);
            if (original == null) {
                log.warn("无法读取图片: {}", inputImage.getName());
                return false;
            }

            log.debug("开始处理图片: {} ({}x{})", inputImage.getName(), original.getWidth(), original.getHeight());

            // 预处理：调整尺寸并标准化
            int inputSize = 1024; // BriaAI RMBG 推荐输入尺寸
            BufferedImage resized = resizeAndPad(original, inputSize);
            float[] inputTensor = preprocessImage(resized, inputSize);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                    new long[]{1, 3, inputSize, inputSize});

            try (OrtSession.Result result = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), input))) {
                
                // 获取输出掩码
                Object outputObj = result.get(0).getValue();
                float[][][][] mask = (float[][][][]) outputObj;

                // 后处理：生成透明背景图片
                BufferedImage alphaImage = postprocessMask(original, mask[0][0], inputSize);

                // 如果输出尺寸需要缩放
                BufferedImage finalImage = alphaImage;
                if (maxOutputSize > 0 && (alphaImage.getWidth() > maxOutputSize || alphaImage.getHeight() > maxOutputSize)) {
                    double scale = (double) maxOutputSize / Math.max(alphaImage.getWidth(), alphaImage.getHeight());
                    int newWidth = (int) (alphaImage.getWidth() * scale);
                    int newHeight = (int) (alphaImage.getHeight() * scale);
                    finalImage = Scalr.resize(alphaImage, Scalr.Method.QUALITY, newWidth, newHeight);
                    log.debug("输出图片缩放: {}x{} -> {}x{}", alphaImage.getWidth(), alphaImage.getHeight(), newWidth, newHeight);
                }

                // 确保输出目录存在
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // 保存 PNG（支持透明）
                savePNG(finalImage, outputFile);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("背景移除完成: {} -> {} ({}ms)", 
                        inputImage.getName(), outputFile.getName(), duration);
                
                return true;
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.error("背景移除失败: {}", inputImage.getName(), e);
            return false;
        }
    }

    /**
     * 带人脸区域优化的背景移除（核心实现）
     * 
     * @param inputImage 输入图片
     * @param outputFile 输出 PNG 文件
     * @param maxOutputSize 输出图片的最大尺寸
     * @param faceRegions 人脸区域列表（用于优化掩码）
     * @return 是否成功
     */
    public boolean removeBackground(File inputImage, File outputFile, int maxOutputSize, java.util.List<java.awt.Rectangle> faceRegions) {
        if (!enabled) {
            log.debug("背景移除功能已禁用");
            return false;
        }

        ensureModelLoaded();
        if (!modelLoaded || session == null) {
            log.warn("模型未加载，跳过背景移除: {}", inputImage.getName());
            return false;
        }

        // 如果没有人脸区域，降级到普通处理
        if (faceRegions == null || faceRegions.isEmpty()) {
            return removeBackground(inputImage, outputFile, maxOutputSize);
        }

        long startTime = System.currentTimeMillis();
        
        try {
            // 读取图片
            BufferedImage original = ImageIO.read(inputImage);
            if (original == null) {
                log.warn("无法读取图片: {}", inputImage.getName());
                return false;
            }

            log.debug("开始处理图片(带人脸优化): {} ({}x{})", inputImage.getName(), original.getWidth(), original.getHeight());

            // 预处理：调整尺寸并标准化
            int inputSize = 1024; // BriaAI RMBG 推荐输入尺寸
            BufferedImage resized = resizeAndPad(original, inputSize);
            float[] inputTensor = preprocessImage(resized, inputSize);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                    new long[]{1, 3, inputSize, inputSize});

            try (OrtSession.Result result = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), input))) {
                
                // 获取输出掩码
                Object outputObj = result.get(0).getValue();
                float[][][][] mask = (float[][][][]) outputObj;

                // 后处理：生成透明背景图片（带人脸区域优化）
                BufferedImage alphaImage = postprocessMask(original, mask[0][0], inputSize, faceRegions);

                // 如果输出尺寸需要缩放
                BufferedImage finalImage = alphaImage;
                if (maxOutputSize > 0 && (alphaImage.getWidth() > maxOutputSize || alphaImage.getHeight() > maxOutputSize)) {
                    double scale = (double) maxOutputSize / Math.max(alphaImage.getWidth(), alphaImage.getHeight());
                    int newWidth = (int) (alphaImage.getWidth() * scale);
                    int newHeight = (int) (alphaImage.getHeight() * scale);
                    finalImage = Scalr.resize(alphaImage, Scalr.Method.QUALITY, newWidth, newHeight);
                }

                // 确保输出目录存在
                File parentDir = outputFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // 保存 PNG（支持透明）
                savePNG(finalImage, outputFile);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("背景移除完成(带人脸优化): {} -> {} ({}ms)", 
                        inputImage.getName(), outputFile.getName(), duration);
                
                return true;
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.error("背景移除失败: {}", inputImage.getName(), e);
            return false;
        }
    }

    /**
     * 移除图片背景，返回 BufferedImage（用于内存中处理）
     */
    public BufferedImage removeBackground(File inputImage) {
        return removeBackground(inputImage, outputMaxSize);
    }

    /**
     * 移除图片背景，返回 BufferedImage（支持自定义输出尺寸）
     */
    public BufferedImage removeBackground(File inputImage, int maxOutputSize) {
        if (!enabled || !modelLoaded || session == null) {
            return null;
        }

        try {
            BufferedImage original = ImageIO.read(inputImage);
            if (original == null) {
                return null;
            }

            int inputSize = 1024;
            BufferedImage resized = resizeAndPad(original, inputSize);
            float[] inputTensor = preprocessImage(resized, inputSize);

            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                    new long[]{1, 3, inputSize, inputSize});

            try (OrtSession.Result result = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), input))) {
                
                Object outputObj = result.get(0).getValue();
                float[][][][] mask = (float[][][][]) outputObj;
                
                BufferedImage alphaImage = postprocessMask(original, mask[0][0], inputSize);
                
                // 如果输出尺寸需要缩放
                if (maxOutputSize > 0 && (alphaImage.getWidth() > maxOutputSize || alphaImage.getHeight() > maxOutputSize)) {
                    double scale = (double) maxOutputSize / Math.max(alphaImage.getWidth(), alphaImage.getHeight());
                    int newWidth = (int) (alphaImage.getWidth() * scale);
                    int newHeight = (int) (alphaImage.getHeight() * scale);
                    return Scalr.resize(alphaImage, Scalr.Method.QUALITY, newWidth, newHeight);
                }
                
                return alphaImage;
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.error("内存中背景移除失败: {}", inputImage.getName(), e);
            return null;
        }
    }

    /**
     * 并发背景移除 - 带缓存检查和任务追踪
     * 用于处理前端频繁的hover请求
     * 
     * @param photoId 图片ID（用于追踪任务）
     * @param sourceFile 源图片文件
     * @param outputFile 输出文件（可选，为null时只返回内存图片）
     * @return 处理后的图片，null表示失败或跳过
     */
    public BufferedImage removeBackgroundConcurrently(Long photoId, File sourceFile, File outputFile) {
        // 使用默认的 outputMaxSize
        return removeBackgroundConcurrently(photoId, sourceFile, outputFile, outputMaxSize);
    }

    /**
     * 并发背景移除 - 带缓存检查和任务追踪，支持指定输出尺寸
     * 
     * @param photoId 图片ID（用于追踪任务）
     * @param sourceFile 源图片文件
     * @param outputFile 输出文件（可选，为null时只返回内存图片）
     * @param outputMaxSize 输出图片的最大边长
     * @return 处理后的图片，null表示失败或跳过
     */
    public BufferedImage removeBackgroundConcurrently(Long photoId, File sourceFile, File outputFile, int outputMaxSize) {
        if (!enabled || !modelLoaded) {
            log.warn("模型未就绪，跳过处理: photoId={}", photoId);
            return null;
        }

        // 检查是否已有任务在处理中
        if (inProgressTasks.containsKey(photoId)) {
            java.util.concurrent.Future<?> existingTask = inProgressTasks.get(photoId);
            if (existingTask != null && !existingTask.isDone()) {
                log.debug("图片正在处理中，等待完成: photoId={}", photoId);
                try {
                    // 等待现有任务完成（最多等待30秒）
                    existingTask.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    // 任务完成后，检查是否有缓存文件
                    if (outputFile != null && outputFile.exists()) {
                        return ImageIO.read(outputFile);
                    }
                    return null;
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("等待处理超时，取消任务: photoId={}", photoId);
                    inProgressTasks.remove(photoId);
                } catch (Exception e) {
                    log.warn("等待处理失败: photoId={}, error={}", photoId, e.getMessage());
                    inProgressTasks.remove(photoId);
                }
            }
        }

        // 提交新任务
        java.util.concurrent.Future<?> task = processingExecutor.submit(() -> {
            try {
                log.info("开始处理抠图: photoId={}, file={}, outputMaxSize={}", photoId, sourceFile.getName(), outputMaxSize);
                
                // 执行背景移除（使用指定的输出尺寸）
                BufferedImage result = removeBackground(sourceFile, outputMaxSize);
                
                if (result != null && outputFile != null) {
                    // 保存到文件
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    ImageIO.write(result, "PNG", outputFile);
                    log.info("抠图完成并保存: photoId={}", photoId);
                }
                
                return result;
            } catch (Exception e) {
                log.error("抠图处理异常: photoId={}", photoId, e);
                return null;
            } finally {
                // 任务完成后移除
                inProgressTasks.remove(photoId);
            }
        });

        inProgressTasks.put(photoId, task);
        
        // 等待任务完成（最多30秒）
        try {
            BufferedImage result = (BufferedImage) task.get(30, java.util.concurrent.TimeUnit.SECONDS);
            if (result != null && outputFile != null && !outputFile.exists()) {
                // 确保文件已保存
                ImageIO.write(result, "PNG", outputFile);
            }
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("处理超时: photoId={}", photoId);
            task.cancel(true);
            inProgressTasks.remove(photoId);
            return null;
        } catch (Exception e) {
            log.error("处理失败: photoId={}", photoId, e);
            inProgressTasks.remove(photoId);
            return null;
        }
    }

    /**
     * 批量处理目录下的所有图片
     */
    public int batchProcess(File inputDir, File outputDir, String[] extensions) {
        if (!enabled || !modelLoaded) {
            log.warn("模型未加载，无法批量处理");
            return 0;
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        int count = 0;
        File[] files = inputDir.listFiles((dir, name) -> {
            for (String ext : extensions) {
                if (name.toLowerCase().endsWith(ext.toLowerCase())) {
                    return true;
                }
            }
            return false;
        });

        if (files == null || files.length == 0) {
            log.info("目录下没有找到需要处理的图片: {}", inputDir.getPath());
            return 0;
        }

        log.info("开始批量处理 {} 张图片...", files.length);

        for (File file : files) {
            String outputName = file.getName();
            // 替换扩展名为 .png
            int dotIndex = outputName.lastIndexOf('.');
            if (dotIndex > 0) {
                outputName = outputName.substring(0, dotIndex) + "_no_bg.png";
            } else {
                outputName = outputName + "_no_bg.png";
            }
            File outputFile = new File(outputDir, outputName);
            
            if (removeBackground(file, outputFile)) {
                count++;
            }
        }

        log.info("批量处理完成: {}/{} 张成功", count, files.length);
        return count;
    }

    /**
     * 调整图片尺寸并填充到正方形
     */
    private BufferedImage resizeAndPad(BufferedImage original, int targetSize) {
        int w = original.getWidth();
        int h = original.getHeight();
        
        // 保持宽高比缩放
        double scale = (double) targetSize / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);
        
        BufferedImage scaled = Scalr.resize(original, Scalr.Method.QUALITY, newW, newH);
        
        // 创建带 padding 的正方形图片
        BufferedImage result = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetSize, targetSize);
        g.drawImage(scaled, (targetSize - newW) / 2, (targetSize - newH) / 2, null);
        g.dispose();
        
        return result;
    }

    /**
     * 预处理图片为模型输入张量
     */
    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                // 归一化到 [-1, 1] 范围
                float rNorm = (r / 255.0f - MEAN[0]) / STD[0];
                float gNorm = (g / 255.0f - MEAN[1]) / STD[1];
                float bNorm = (b / 255.0f - MEAN[2]) / STD[2];
                
                // BGR 通道顺序
                int idx = y * size + x;
                tensor[idx] = bNorm;
                tensor[idx + size * size] = gNorm;
                tensor[idx + 2 * size * size] = rNorm;
            }
        }
        
        return tensor;
    }

    /**
     * 后处理掩码，生成带透明通道的图片
     */
    private BufferedImage postprocessMask(BufferedImage original, float[][] mask, int maskSize) {
        int w = original.getWidth();
        int h = original.getHeight();

        // 创建带透明通道的图片
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();

        // 计算掩码在原图中的位置
        double scale = (double) maskSize / Math.max(w, h);
        int maskW = (int) (w * scale);
        int maskH = (int) (h * scale);
        int offsetX = (maskSize - maskW) / 2;
        int offsetY = (maskSize - maskH) / 2;

        // 应用掩码
        int[] pixels = new int[w * h];
        result.getRGB(0, 0, w, h, pixels, 0, w);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // 将原图坐标映射到掩码坐标
                int maskX = (int) (x * scale) + offsetX;
                int maskY = (int) (y * scale) + offsetY;
                
                // 确保在掩码范围内
                if (maskX < 0 || maskX >= maskSize || maskY < 0 || maskY >= maskSize) {
                    continue;
                }
                
                float alpha = mask[maskY][maskX];
                
                // 应用阈值和羽化
                alpha = smoothAlpha(alpha);
                
                int idx = y * w + x;
                int rgb = pixels[idx];
                
                // 设置新的 alpha 值
                int newAlpha = (int) (alpha * 255) << 24;
                pixels[idx] = (rgb & 0x00FFFFFF) | newAlpha;
            }
        }

        result.setRGB(0, 0, w, h, pixels, 0, w);
        
        // 后处理：腐蚀去除噪点
        if (erodeRadius > 0) {
            result = erodeAlpha(result, erodeRadius);
        }
        
        // 后处理：高斯模糊平滑边缘
        if (blurRadius > 0) {
            result = blurAlpha(result, blurRadius);
        }
        
        return result;
    }

    /**
     * 后处理掩码（带人脸区域优化），生成带透明通道的图片
     * 人脸区域周围的掩码更宽松，远离人脸的区域更严格
     */
    private BufferedImage postprocessMask(BufferedImage original, float[][] mask, int maskSize, 
            java.util.List<java.awt.Rectangle> faceRegions) {
        int w = original.getWidth();
        int h = original.getHeight();

        // 计算所有人脸区域的合并区域（带扩展）
        int padding = (int) (Math.min(w, h) * 0.3); // 扩展30%的图片尺寸
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
        
        for (java.awt.Rectangle face : faceRegions) {
            int expandedX = Math.max(0, face.x - padding);
            int expandedY = Math.max(0, face.y - padding);
            int expandedW = face.width + padding * 2;
            int expandedH = face.height + padding * 2;
            
            minX = Math.min(minX, expandedX);
            minY = Math.min(minY, expandedY);
            maxX = Math.max(maxX, expandedX + expandedW);
            maxY = Math.max(maxY, expandedY + expandedH);
        }
        
        // 确保不超出图片边界
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(w, maxX);
        maxY = Math.min(h, maxY);
        
        final int faceRegionMinX = minX;
        final int faceRegionMinY = minY;
        final int faceRegionMaxX = maxX;
        final int faceRegionMaxY = maxY;
        
        // 人脸区域内外的不同阈值
        final float faceRegionThreshold = thresholdTransparent * 0.7f; // 区域内更宽松
        final float outsideThreshold = thresholdTransparent * 1.5f; // 区域外更严格
        
        log.debug("人脸优化区域: ({}, {}) - ({}, {})", minX, minY, maxX, maxY);

        // 创建带透明通道的图片
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();

        // 计算掩码在原图中的位置
        double scale = (double) maskSize / Math.max(w, h);
        int maskW = (int) (w * scale);
        int maskH = (int) (h * scale);
        int offsetX = (maskSize - maskW) / 2;
        int offsetY = (maskSize - maskH) / 2;

        // 应用掩码
        int[] pixels = new int[w * h];
        result.getRGB(0, 0, w, h, pixels, 0, w);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // 将原图坐标映射到掩码坐标
                int maskX = (int) (x * scale) + offsetX;
                int maskY = (int) (y * scale) + offsetY;
                
                // 确保在掩码范围内
                if (maskX < 0 || maskX >= maskSize || maskY < 0 || maskY >= maskSize) {
                    continue;
                }
                
                float alpha = mask[maskY][maskX];
                
                // 根据是否在人脸区域应用不同的阈值
                boolean inFaceRegion = x >= faceRegionMinX && x <= faceRegionMaxX && 
                                        y >= faceRegionMinY && y <= faceRegionMaxY;
                float effectiveThreshold = inFaceRegion ? faceRegionThreshold : outsideThreshold;
                
                // 应用阈值和羽化
                alpha = smoothAlpha(alpha, effectiveThreshold);
                
                int idx = y * w + x;
                int rgb = pixels[idx];
                
                // 设置新的 alpha 值
                int newAlpha = (int) (alpha * 255) << 24;
                pixels[idx] = (rgb & 0x00FFFFFF) | newAlpha;
            }
        }

        result.setRGB(0, 0, w, h, pixels, 0, w);
        
        // 后处理：腐蚀去除噪点
        if (erodeRadius > 0) {
            result = erodeAlpha(result, erodeRadius);
        }
        
        // 后处理：高斯模糊平滑边缘
        if (blurRadius > 0) {
            result = blurAlpha(result, blurRadius);
        }
        
        return result;
    }
    
    /**
     * 腐蚀 alpha 通道，去除边缘噪点
     */
    private BufferedImage erodeAlpha(BufferedImage src, int radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getRGB(0, 0, w, h, pixels, 0, w);
        
        int[] result = new int[w * h];
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // 找到周围最小的 alpha 值
                float minAlpha = 1.0f;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            int idx = ny * w + nx;
                            int alpha = (pixels[idx] >> 24) & 0xFF;
                            minAlpha = Math.min(minAlpha, alpha / 255.0f);
                        }
                    }
                }
                int idx = y * w + x;
                int newAlpha = (int) (minAlpha * 255);
                result[idx] = (pixels[idx] & 0x00FFFFFF) | (newAlpha << 24);
            }
        }
        
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        dst.setRGB(0, 0, w, h, result, 0, w);
        return dst;
    }
    
    /**
     * 高斯模糊 alpha 通道，平滑边缘
     */
    private BufferedImage blurAlpha(BufferedImage src, int radius) {
        // 使用简单的盒式模糊近似高斯模糊
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getRGB(0, 0, w, h, pixels, 0, w);
        
        int[] temp = new int[w * h];
        int[] result = new int[w * h];
        
        // 水平模糊
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                int count = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int nx = x + dx;
                    if (nx >= 0 && nx < w) {
                        int alpha = (pixels[y * w + nx] >> 24) & 0xFF;
                        sum += alpha;
                        count++;
                    }
                }
                temp[y * w + x] = (int) (sum / count);
            }
        }
        
        // 垂直模糊
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                int count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int ny = y + dy;
                    if (ny >= 0 && ny < h) {
                        sum += temp[ny * w + x];
                        count++;
                    }
                }
                int avgAlpha = (int) (sum / count);
                int idx = y * w + x;
                result[idx] = (pixels[idx] & 0x00FFFFFF) | (avgAlpha << 24);
            }
        }
        
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        dst.setRGB(0, 0, w, h, result, 0, w);
        return dst;
    }

    /**
     * 平滑 alpha 值，使用 sigmoid 函数使边缘更自然
     */
    private float smoothAlpha(float alpha) {
        return smoothAlpha(alpha, thresholdTransparent);
    }
    
    /**
     * 平滑 alpha 值，使用自定义阈值
     */
    private float smoothAlpha(float alpha, float customThreshold) {
        // 阈值处理，去除弱置信度区域
        if (alpha < customThreshold) {
            return 0f;
        }
        if (alpha > thresholdSolid) {
            return 1f;
        }
        // 使用 sigmoid 平滑过渡，使边缘更干净
        float mid = (customThreshold + thresholdSolid) / 2;
        float range = thresholdSolid - customThreshold;
        float normalized = (alpha - mid) / (range / 2);
        // Sigmoid 函数: 1 / (1 + e^(-x * steepness))
        float steepness = 3.0f;
        float sigmoid = 1.0f / (1.0f + (float) Math.exp(-normalized * steepness));
        return sigmoid;
    }

    /**
     * 保存为 PNG 格式
     */
    private void savePNG(BufferedImage image, File outputFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(outputFile);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(fos)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
            writer.setOutput(ios);
            writer.write(image);
            writer.dispose();
        }
        fos.close();
    }

    /**
     * 记录系统诊断信息
     */
    private void logSystemInfo() {
        try {
            log.warn("Java版本: {}", System.getProperty("java.version"));
            log.warn("操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
            log.warn("系统架构: {}", System.getProperty("os.arch"));
            log.warn("CPU核心数: {}", Runtime.getRuntime().availableProcessors());
        } catch (Exception e) {
            log.warn("收集系统信息时出错: {}", e.getMessage());
        }
    }

    /**
     * 检查模型是否可用
     */
    public boolean isModelAvailable() {
        return enabled && modelLoaded && session != null;
    }

    @Override
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
    }
}
