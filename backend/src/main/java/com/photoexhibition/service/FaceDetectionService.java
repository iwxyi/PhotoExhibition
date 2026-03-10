package com.photoexhibition.service;

import ai.onnxruntime.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.lang.UnsatisfiedLinkError;

/**
 * 基于ONNX模型的专业人脸检测服务
 * 支持RetinaFace、MTCNN等专业检测模型
 */
@Slf4j
@Service
public class FaceDetectionService implements AutoCloseable {

    @Value("${face.detection.model-path:./models/face_detection.onnx}")
    private String detectionModelPath;

    @Value("${face.detection.enabled:false}")
    private boolean enabled;

    @Value("${face.detection.input-size:640}")
    private int inputSize;

    @Value("${face.detection.confidence-threshold:0.5}")
    private double confidenceThreshold;

    @Value("${face.detection.nms-threshold:0.4}")
    private double nmsThreshold;

    private OrtEnvironment env;
    private OrtSession detectionSession;
    // RetinaFace 风格模型的先验框（center_x, center_y, width, height，单位：模型输入像素）
    private List<double[]> priors;
    private int priorsInputSize = -1;
    // RetinaFace decode 常用方差
    private static final double[] VARIANCES = new double[]{0.1, 0.2};
    // 为了避免在低阈值时产生过多候选导致 NMS 和后处理过慢，限制参与 NMS 的最大候选数
    private static final int MAX_NMS_CANDIDATES = 2000;

    // 控制人脸检测的详细日志（批量扫描时关闭，单张重建时开启）
    public static final ThreadLocal<Boolean> VERBOSE_LOG = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isVerboseLog() {
        return Boolean.TRUE.equals(VERBOSE_LOG.get());
    }

    /**
     * 使用ONNX模型检测人脸
     * 如果模型未配置或加载失败，返回空列表（会回退到简单检测方法）
     */
    public List<DetectedFace> detectFaces(File imageFile) {
        if (!enabled) {
            return new ArrayList<>();
        }
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) return new ArrayList<>();
            return detectFaces(img);
        } catch (Exception e) {
            log.warn("ONNX人脸检测失败: {}", imageFile.getName(), e);
            return new ArrayList<>();
        }
    }

    public List<DetectedFace> detectFaces(BufferedImage img) {
        if (!enabled || img == null) {
            return new ArrayList<>();
        }

        try {
            ensureSession();
            if (detectionSession == null) {
                return new ArrayList<>();
            }

            // 预处理图像
            int size = Math.max(320, Math.min(2048, inputSize)); // 限定范围，防止过大
            BufferedImage resized = resizeImage(img, size);
            float[] inputTensor = preprocessImage(resized, size);

            // 确保先验框已为当前输入尺寸生成
            ensurePriors(size);

            // 运行推理
            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputTensor),
                new long[]{1, 3, size, size});
            
            try (OrtSession.Result result = detectionSession.run(
                Collections.singletonMap(detectionSession.getInputNames().iterator().next(), input))) {
                // 调试输出：仅在详细模式下打印输出信息
                if (isVerboseLog()) {
                    try {
                        int outputIndex = 0;
                        for (String outName : detectionSession.getOutputNames()) {
                            Object value = result.get(outputIndex);
                            if (value instanceof OnnxTensor) {
                                OnnxTensor tensor = (OnnxTensor) value;
                                long[] shape = tensor.getInfo().getShape();
                                log.debug("人脸检测ONNX输出[{}] name={} class={} shape={}",
                                        outputIndex,
                                        outName,
                                        tensor.getClass().getName(),
                                        java.util.Arrays.toString(shape));
                            } else if (value != null) {
                                log.debug("人脸检测ONNX输出[{}] name={} class={}",
                                        outputIndex,
                                        outName,
                                        value.getClass().getName());
                            } else {
                                log.debug("人脸检测ONNX输出[{}] name={} 为null", outputIndex, outName);
                            }
                            outputIndex++;
                        }
                    } catch (Exception e) {
                        log.debug("打印人脸检测ONNX输出信息时出错: {}", e.getMessage());
                    }
                }

                // 解析输出：使用 Retina 风格偏移解码（bbox 视为先验偏移）
                if (result.size() >= 2) {
                    Object boxesObj = result.get(0).getValue();
                    Object scoresObj = result.get(1).getValue();

                    if (isVerboseLog()) {
                        log.debug("人脸检测ONNX原始输出类型: boxes={}, scores={}",
                                boxesObj != null ? boxesObj.getClass().getName() : "null",
                                scoresObj != null ? scoresObj.getClass().getName() : "null");
                    }

                    // 打印前几个 bbox 原始值，方便判断坐标格式
                    if (isVerboseLog()) {
                        try {
                            if (boxesObj instanceof float[][][]) {
                                float[][][] bb = (float[][][]) boxesObj;
                                for (int i = 0; i < Math.min(3, bb[0].length); i++) {
                                    log.debug("bbox sample[{}]={}", i, java.util.Arrays.toString(bb[0][i]));
                                }
                            }
                        } catch (Exception ignore) {}
                    }

                    if (boxesObj instanceof float[][][] && scoresObj instanceof float[][][]) {
                        float[][][] boxes = (float[][][]) boxesObj;
                        float[][][] scores = (float[][][]) scoresObj;

                        List<DetectedFace> facesPrior = parseDetectionsRetinaStyle(boxes, scores, img.getWidth(), img.getHeight(), size);
                        if (isVerboseLog()) {
                            for (int i = 0; i < Math.min(5, facesPrior.size()); i++) {
                                DetectedFace f = facesPrior.get(i);
                                log.debug("decoded(face prior)[{}]: x={} y={} w={} h={} area={}", i,
                                        format4(f.getX()), format4(f.getY()), format4(f.getWidth()), format4(f.getHeight()),
                                        format4(f.getWidth() * f.getHeight()));
                            }
                        }
                        if (isVerboseLog()) {
                            log.debug("人脸检测ONNX解析得到 {} 个候选框（解析前未做NMS，解码方案=prior）", facesPrior.size());
                        }
                        return applyNMS(facesPrior);
                    }
                    // 旧格式兜底：scores=[1,N]
                    if (boxesObj instanceof float[][][] && scoresObj instanceof float[][]) {
                        float[][][] boxes = (float[][][]) boxesObj;
                        float[][] scores = (float[][]) scoresObj;

                        List<DetectedFace> faces = parseDetections(boxes, scores, img.getWidth(), img.getHeight(), size);
                        if (isVerboseLog()) {
                            log.debug("人脸检测ONNX解析得到 {} 个候选框（解析前未做NMS，旧格式兜底）", faces.size());
                        }
                        return applyNMS(faces);
                    }
                    log.warn("人脸检测ONNX输出格式与当前解析逻辑不匹配，未返回检测结果。");
                } else {
                    log.warn("人脸检测ONNX返回的输出数量不足2个，当前解析逻辑无法处理。输出个数={}", result.size());
                }
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("ONNX人脸检测失败: {}", e.getMessage());
        }
        
        return new ArrayList<>();
    }

    private void ensureSession() {
        if (detectionSession != null) return;
        
        try {
            java.io.File modelFile = new java.io.File(detectionModelPath);
            if (!modelFile.exists()) {
                log.warn("人脸检测模型文件不存在: {}，将使用简单检测方法", detectionModelPath);
                return;
            }

            // 预先检查ONNX Runtime是否可用
            try {
                Class.forName("ai.onnxruntime.OrtEnvironment");
            } catch (ClassNotFoundException e) {
                log.warn("ONNX Runtime类不存在，人脸检测功能将被禁用。错误: {}", e.getMessage());
                return;
            }
            
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            // 可以根据需要设置线程数等选项
            detectionSession = env.createSession(detectionModelPath, opts);
            log.info("人脸检测模型已加载: {}", detectionModelPath);
        } catch (NoClassDefFoundError e) {
            log.warn("ONNX Runtime类初始化失败，人脸检测功能将被禁用。错误: {}", e.getMessage());
            log.warn("请检查: 1) ONNX Runtime JAR文件是否完整, 2) Java版本是否兼容 (>=11), 3) 系统权限是否足够");
            log.warn("详细错误信息: ", e);
            detectionSession = null;
        } catch (UnsatisfiedLinkError e) {
            log.warn("ONNX Runtime本地库缺失，人脸检测功能将被禁用。错误: {}", e.getMessage());
            log.warn("系统诊断信息:");
            logSystemInfo();
            log.warn("可能解决方案:");
            log.warn("1. Windows: 安装 Visual C++ Redistributable 2015-2022 (x64)");
            log.warn("2. Linux: 安装 libgomp1, libatlas-base-dev, libopenblas-dev");
            log.warn("3. macOS: 安装通过 Homebrew 安装的依赖");
            log.warn("4. 检查文件权限和系统路径");
            log.warn("详细错误信息: ", e);
            detectionSession = null;
        } catch (Exception e) {
            log.warn("加载人脸检测模型失败: {}，将使用简单检测方法。错误: {}", detectionModelPath, e.getMessage());
            log.warn("详细错误信息: ", e);
            detectionSession = null;
        }
    }

    private BufferedImage resizeImage(BufferedImage img, int targetSize) {
        int w = img.getWidth();
        int h = img.getHeight();
        
        // 保持宽高比，短边缩放到targetSize
        double scale = (double) targetSize / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);
        
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, targetSize, targetSize);
        g.drawImage(img, (targetSize - newW) / 2, (targetSize - newH) / 2, newW, newH, null);
        g.dispose();
        
        return resized;
    }

    /**
     * RetinaFace 预处理：BGR 顺序，减均值 [104,117,123]，不缩放到 [-1,1]
     * 这里假定传入的 img 已经是 size x size 的 letterbox 图（由 resizeImage 生成）
     */
    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        int idx = 0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                // BGR 通道顺序
                tensor[idx] = (b - 104.0f);                  // B
                tensor[idx + size * size] = (g - 117.0f);    // G
                tensor[idx + 2 * size * size] = (r - 123.0f);// R
                idx++;
            }
        }

        return tensor;
    }

    /**
     * 为 RetinaFace 风格模型生成先验框
     * 约定：strides=[8,16,32]，每个位置2个 anchor，对应 min_sizes：
     * [16,32] / [64,128] / [256,512]
     */
    private void ensurePriors(int input) {
        if (priors != null && priorsInputSize == input) return;

        List<double[]> result = new ArrayList<>();

        int[] strides = new int[]{8, 16, 32};
        int[][] minSizes = new int[][]{
            {16, 32},
            {64, 128},
            {256, 512}
        };

        for (int idx = 0; idx < strides.length; idx++) {
            int stride = strides[idx];
            int[] ms = minSizes[idx];
            int fmSize = input / stride; // 假定能整除

            for (int i = 0; i < fmSize; i++) {
                for (int j = 0; j < fmSize; j++) {
                    double cx = (j + 0.5) * stride;
                    double cy = (i + 0.5) * stride;
                    for (int m : ms) {
                        double w = m;
                        double h = m;
                        result.add(new double[]{cx, cy, w, h});
                    }
                }
            }
        }

        this.priors = result;
        this.priorsInputSize = input;
        log.info("人脸检测先验框已生成: inputSize={}，priors={}", input, result.size());
    }

    /**
     * 解析 RetinaFace 风格输出：
     * boxes=[1,N,4]（dx,dy,dw,dh，相对先验框的偏移），scores=[1,N,2]（背景、人脸）
     */
    private List<DetectedFace> parseDetectionsRetinaStyle(float[][][] boxes, float[][][] scores,
                                               int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();

        if (boxes.length == 0 || boxes[0].length == 0 || scores.length == 0 || scores[0].length == 0) {
            return faces;
        }

        if (priors == null || priorsInputSize != inputSize || priors.size() != boxes[0].length) {
            log.warn("RetinaFace 先验框数量与模型输出不匹配: priors={} outputN={} inputSize={}",
                    priors != null ? priors.size() : -1, boxes[0].length, inputSize);
            return faces;
        }

        // 与 resizeImage 中保持一致：长边缩放到 inputSize，短边居中填充
        int w = originalWidth;
        int h = originalHeight;
        double scale = (double) inputSize / Math.max(w, h);
        double newW = w * scale;
        double newH = h * scale;
        double padX = (inputSize - newW) / 2.0;
        double padY = (inputSize - newH) / 2.0;

        int numAnchors = boxes[0].length;
        int passedScoreCount = 0; // 仅通过置信度阈值的数量
        int validCount = 0;       // 最终保留下来的数量（包含几何过滤）
        double maxProb = 0.0;
        double effectiveThreshold = confidenceThreshold;

        // 调试：记录前若干个最高概率的人脸框（解码后的输入坐标 + 归一化坐标）
        final int TOP_K_DEBUG = 5;
        double[] topProbs = new double[TOP_K_DEBUG];
        double[][] topRaw = new double[TOP_K_DEBUG][4];     // x1,y1,x2,y2（模型输入坐标）
        double[][] topNorm = new double[TOP_K_DEBUG][4];    // x,y,w,h（归一化0-1）

        for (int i = 0; i < numAnchors; i++) {
            float[] scoreArr = scores[0][i];
            if (scoreArr == null || scoreArr.length == 0) continue;

            // 通常 scores[0]=背景，scores[1]=人脸，很多实现是 logits 形式，这里做 softmax 得到概率
            float bgLogit = scoreArr[0];
            float faceLogit = scoreArr.length > 1 ? scoreArr[1] : scoreArr[0];
            double maxLogit = Math.max(bgLogit, faceLogit);
            double expBg = Math.exp(bgLogit - maxLogit);
            double expFace = Math.exp(faceLogit - maxLogit);
            double probFace = expFace / (expBg + expFace);

            if (probFace > maxProb) {
                maxProb = probFace;
            }

            if (probFace < effectiveThreshold) {
                continue;
            }

            // 记录通过置信度过滤的数量
            passedScoreCount++;

            float[] boxArr = boxes[0][i];
            if (boxArr == null || boxArr.length < 4) continue;

            // 先从 loc（dx,dy,dw,dh）+ prior（cx,cy,w,h）解码到模型输入坐标系
            double[] prior = priors.get(i);
            double pCx = prior[0];
            double pCy = prior[1];
            double pW = prior[2];
            double pH = prior[3];

            double dx = boxArr[0];
            double dy = boxArr[1];
            double dw = boxArr[2];
            double dh = boxArr[3];

            double cx = pCx + dx * VARIANCES[0] * pW;
            double cy = pCy + dy * VARIANCES[0] * pH;
            double bwInput = pW * Math.exp(dw * VARIANCES[1]);
            double bhInput = pH * Math.exp(dh * VARIANCES[1]);

            double x1r = cx - bwInput / 2.0;
            double y1r = cy - bhInput / 2.0;
            double x2r = cx + bwInput / 2.0;
            double y2r = cy + bhInput / 2.0;

            // 再从带 padding 的 input 空间，映射回原始图像像素坐标
            double x1p = (x1r - padX) / scale;
            double y1p = (y1r - padY) / scale;
            double x2p = (x2r - padX) / scale;
            double y2p = (y2r - padY) / scale;

            // 边界裁剪
            x1p = Math.max(0.0, Math.min(w, x1p));
            y1p = Math.max(0.0, Math.min(h, y1p));
            x2p = Math.max(0.0, Math.min(w, x2p));
            y2p = Math.max(0.0, Math.min(h, y2p));

            double bw = x2p - x1p;
            double bh = y2p - y1p;

            // 归一化到 [0,1]，与前端/数据库现有坐标体系保持一致
            double x = x1p / w;
            double y = y1p / h;
            double boxW = bw / w;
            double boxH = bh / h;

            // 基本约束，防止明显异常框（这里暂时只做轻微收缩，避免过度过滤）
            x = Math.max(0.0, Math.min(1.0, x));
            y = Math.max(0.0, Math.min(1.0, y));
            boxW = Math.max(0.001, Math.min(1.0 - x, boxW));
            boxH = Math.max(0.001, Math.min(1.0 - y, boxH));

            // 更新调试用的 TOP_K
            for (int k = 0; k < TOP_K_DEBUG; k++) {
                if (probFace > topProbs[k]) {
                    // 后移一位
                    for (int m = TOP_K_DEBUG - 1; m > k; m--) {
                        topProbs[m] = topProbs[m - 1];
                        topRaw[m] = topRaw[m - 1];
                        topNorm[m] = topNorm[m - 1];
                    }
                    topProbs[k] = probFace;
                    topRaw[k] = new double[]{x1r, y1r, x2r, y2r};
                    topNorm[k] = new double[]{x, y, boxW, boxH};
                    break;
                }
            }

            faces.add(new DetectedFace(x, y, boxW, boxH, probFace));
            validCount++;
        }

        if (isVerboseLog()) {
            log.debug("人脸检测ONNX（Retina风格）通过置信度阈值的候选框数量: {}/{}，最终保留（含几何过滤）: {}，最大人脸概率={}，阈值={}",
                    passedScoreCount, numAnchors, validCount,
                    String.format(java.util.Locale.ROOT, "%.4f", maxProb),
                    String.format(java.util.Locale.ROOT, "%.4f", effectiveThreshold));
        }

        // 仅在详细模式下打印 TOP-K 调试信息
        if (isVerboseLog()) {
            for (int k = 0; k < TOP_K_DEBUG; k++) {
                if (topProbs[k] <= 0) continue;
                double[] r = topRaw[k];
                double[] n = topNorm[k];
                log.debug("人脸检测ONNX TOP#{}: prob={} raw=({}, {}, {}, {}) norm=({}, {}, {}, {})",
                        (k + 1),
                        String.format(java.util.Locale.ROOT, "%.4f", topProbs[k]),
                        String.format(java.util.Locale.ROOT, "%.1f", r[0]),
                        String.format(java.util.Locale.ROOT, "%.1f", r[1]),
                        String.format(java.util.Locale.ROOT, "%.1f", r[2]),
                        String.format(java.util.Locale.ROOT, "%.1f", r[3]),
                        String.format(java.util.Locale.ROOT, "%.4f", n[0]),
                        String.format(java.util.Locale.ROOT, "%.4f", n[1]),
                        String.format(java.util.Locale.ROOT, "%.4f", n[2]),
                        String.format(java.util.Locale.ROOT, "%.4f", n[3]));
            }

            // 打印解码后置信度最高的前5个框，便于对照原图
            if (!faces.isEmpty() && isVerboseLog()) {
                List<DetectedFace> top = new ArrayList<>(faces);
                top.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
                for (int i = 0; i < Math.min(5, top.size()); i++) {
                    DetectedFace f = top.get(i);
                    log.debug("decoded(face prior)[{}]: conf={} x={} y={} w={} h={} area={}", i,
                            format4(f.getConfidence()),
                            format4(f.getX()), format4(f.getY()), format4(f.getWidth()), format4(f.getHeight()),
                            format4(f.getWidth() * f.getHeight()));
                }
            }
        }

        if (validCount == 0 && isVerboseLog()) {
            log.debug("人脸检测ONNX：在当前阈值下未保留任何候选框，建议暂时调低 face.detection.confidence-threshold 以观察效果。");
        }
        return faces;
    }

    /**
     * 解析绝对坐标输出（假设 boxes 已经是输入空间的 [x1,y1,x2,y2]）
     */
    private List<DetectedFace> parseDetectionsAbsolute(float[][][] boxes, float[][][] scores,
                                                       int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();
        if (boxes.length == 0 || boxes[0].length == 0 || scores.length == 0 || scores[0].length == 0) {
            return faces;
        }

        int w = originalWidth;
        int h = originalHeight;
        double scale = (double) inputSize / Math.max(w, h);
        double newW = w * scale;
        double newH = h * scale;
        double padX = (inputSize - newW) / 2.0;
        double padY = (inputSize - newH) / 2.0;

        int numAnchors = boxes[0].length;
        for (int i = 0; i < numAnchors; i++) {
            float[] scoreArr = scores[0][i];
            if (scoreArr == null || scoreArr.length == 0) continue;

            float bgLogit = scoreArr[0];
            float faceLogit = scoreArr.length > 1 ? scoreArr[1] : scoreArr[0];
            double maxLogit = Math.max(bgLogit, faceLogit);
            double expBg = Math.exp(bgLogit - maxLogit);
            double expFace = Math.exp(faceLogit - maxLogit);
            double probFace = expFace / (expBg + expFace);
            if (probFace < confidenceThreshold) continue;

            float[] boxArr = boxes[0][i];
            if (boxArr == null || boxArr.length < 4) continue;

            double x1r = boxArr[0];
            double y1r = boxArr[1];
            double x2r = boxArr[2];
            double y2r = boxArr[3];

            double x1p = (x1r - padX) / scale;
            double y1p = (y1r - padY) / scale;
            double x2p = (x2r - padX) / scale;
            double y2p = (y2r - padY) / scale;

            x1p = Math.max(0.0, Math.min(w, x1p));
            y1p = Math.max(0.0, Math.min(h, y1p));
            x2p = Math.max(0.0, Math.min(w, x2p));
            y2p = Math.max(0.0, Math.min(h, y2p));

            double bw = x2p - x1p;
            double bh = y2p - y1p;
            if (bw <= 0 || bh <= 0) continue;

            double xn = x1p / w;
            double yn = y1p / h;
            double wn = bw / w;
            double hn = bh / h;
            xn = Math.max(0.0, Math.min(1.0, xn));
            yn = Math.max(0.0, Math.min(1.0, yn));
            wn = Math.max(0.0, Math.min(1.0 - xn, wn));
            hn = Math.max(0.0, Math.min(1.0 - yn, hn));

            faces.add(new DetectedFace(xn, yn, wn, hn, probFace));
        }
        return faces;
    }

    /**
     * 解析归一化绝对坐标输出（假设 boxes 已经是 [x1,y1,x2,y2]，范围约在0~1，对应 letterbox 后的输入空间）
     */
    private List<DetectedFace> parseDetectionsNormalized(float[][][] boxes, float[][][] scores,
                                                         int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();
        if (boxes.length == 0 || boxes[0].length == 0 || scores.length == 0 || scores[0].length == 0) {
            return faces;
        }

        int numAnchors = boxes[0].length;
        // 映射到原图：先把 [0,1] 归一化坐标映射到 letterbox 输入，再还原到原图
        int w = originalWidth;
        int h = originalHeight;
        double scale = (double) inputSize / Math.max(w, h);
        double newW = w * scale;
        double newH = h * scale;
        double padX = (inputSize - newW) / 2.0;
        double padY = (inputSize - newH) / 2.0;

        for (int i = 0; i < numAnchors; i++) {
            float[] scoreArr = scores[0][i];
            if (scoreArr == null || scoreArr.length == 0) continue;

            float bgLogit = scoreArr[0];
            float faceLogit = scoreArr.length > 1 ? scoreArr[1] : scoreArr[0];
            double maxLogit = Math.max(bgLogit, faceLogit);
            double expBg = Math.exp(bgLogit - maxLogit);
            double expFace = Math.exp(faceLogit - maxLogit);
            double probFace = expFace / (expBg + expFace);
            if (probFace < confidenceThreshold) continue;

            float[] boxArr = boxes[0][i];
            if (boxArr == null || boxArr.length < 4) continue;

            double x1n = boxArr[0];
            double y1n = boxArr[1];
            double x2n = boxArr[2];
            double y2n = boxArr[3];

            // 过滤明显无效的值
            if (Double.isNaN(x1n) || Double.isNaN(y1n) || Double.isNaN(x2n) || Double.isNaN(y2n)) continue;

            // 映射回原图
            double x1p = (x1n * inputSize - padX) / scale;
            double y1p = (y1n * inputSize - padY) / scale;
            double x2p = (x2n * inputSize - padX) / scale;
            double y2p = (y2n * inputSize - padY) / scale;

            x1p = Math.max(0.0, Math.min(w, x1p));
            y1p = Math.max(0.0, Math.min(h, y1p));
            x2p = Math.max(0.0, Math.min(w, x2p));
            y2p = Math.max(0.0, Math.min(h, y2p));

            double bw = x2p - x1p;
            double bh = y2p - y1p;
            if (bw <= 0 || bh <= 0) continue;

            double xn = x1p / w;
            double yn = y1p / h;
            double wn = bw / w;
            double hn = bh / h;
            xn = Math.max(0.0, Math.min(1.0, xn));
            yn = Math.max(0.0, Math.min(1.0, yn));
            wn = Math.max(0.0, Math.min(1.0 - xn, wn));
            hn = Math.max(0.0, Math.min(1.0 - yn, hn));

            faces.add(new DetectedFace(xn, yn, wn, hn, probFace));
        }
        return faces;
    }

    /**
     * 解析绝对坐标（无 padding，等比拉伸到 inputSize）假设 boxes 是输入空间像素坐标
     */
    private List<DetectedFace> parseDetectionsAbsoluteStretch(float[][][] boxes, float[][][] scores,
                                                              int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();
        if (boxes.length == 0 || boxes[0].length == 0 || scores.length == 0 || scores[0].length == 0) {
            return faces;
        }

        int w = originalWidth;
        int h = originalHeight;

        int numAnchors = boxes[0].length;
        for (int i = 0; i < numAnchors; i++) {
            float[] scoreArr = scores[0][i];
            if (scoreArr == null || scoreArr.length == 0) continue;

            float bgLogit = scoreArr[0];
            float faceLogit = scoreArr.length > 1 ? scoreArr[1] : scoreArr[0];
            double maxLogit = Math.max(bgLogit, faceLogit);
            double expBg = Math.exp(bgLogit - maxLogit);
            double expFace = Math.exp(faceLogit - maxLogit);
            double probFace = expFace / (expBg + expFace);
            if (probFace < confidenceThreshold) continue;

            float[] boxArr = boxes[0][i];
            if (boxArr == null || boxArr.length < 4) continue;

            double x1p = (boxArr[0] / inputSize) * w;
            double y1p = (boxArr[1] / inputSize) * h;
            double x2p = (boxArr[2] / inputSize) * w;
            double y2p = (boxArr[3] / inputSize) * h;

            x1p = Math.max(0.0, Math.min(w, x1p));
            y1p = Math.max(0.0, Math.min(h, y1p));
            x2p = Math.max(0.0, Math.min(w, x2p));
            y2p = Math.max(0.0, Math.min(h, y2p));

            double bw = x2p - x1p;
            double bh = y2p - y1p;
            if (bw <= 0 || bh <= 0) continue;

            double xn = x1p / w;
            double yn = y1p / h;
            double wn = bw / w;
            double hn = bh / h;
            xn = Math.max(0.0, Math.min(1.0, xn));
            yn = Math.max(0.0, Math.min(1.0, yn));
            wn = Math.max(0.0, Math.min(1.0 - xn, wn));
            hn = Math.max(0.0, Math.min(1.0 - yn, hn));

            faces.add(new DetectedFace(xn, yn, wn, hn, probFace));
        }
        return faces;
    }

    /**
     * 解析归一化坐标（无 padding，等比拉伸到 inputSize），假设 boxes 是 0~1 范围
     */
    private List<DetectedFace> parseDetectionsNormalizedStretch(float[][][] boxes, float[][][] scores,
                                                                int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();
        if (boxes.length == 0 || boxes[0].length == 0 || scores.length == 0 || scores[0].length == 0) {
            return faces;
        }

        int w = originalWidth;
        int h = originalHeight;

        int numAnchors = boxes[0].length;
        for (int i = 0; i < numAnchors; i++) {
            float[] scoreArr = scores[0][i];
            if (scoreArr == null || scoreArr.length == 0) continue;

            float bgLogit = scoreArr[0];
            float faceLogit = scoreArr.length > 1 ? scoreArr[1] : scoreArr[0];
            double maxLogit = Math.max(bgLogit, faceLogit);
            double expBg = Math.exp(bgLogit - maxLogit);
            double expFace = Math.exp(faceLogit - maxLogit);
            double probFace = expFace / (expBg + expFace);
            if (probFace < confidenceThreshold) continue;

            float[] boxArr = boxes[0][i];
            if (boxArr == null || boxArr.length < 4) continue;

            double x1p = boxArr[0] * w;
            double y1p = boxArr[1] * h;
            double x2p = boxArr[2] * w;
            double y2p = boxArr[3] * h;

            x1p = Math.max(0.0, Math.min(w, x1p));
            y1p = Math.max(0.0, Math.min(h, y1p));
            x2p = Math.max(0.0, Math.min(w, x2p));
            y2p = Math.max(0.0, Math.min(h, y2p));

            double bw = x2p - x1p;
            double bh = y2p - y1p;
            if (bw <= 0 || bh <= 0) continue;

            double xn = x1p / w;
            double yn = y1p / h;
            double wn = bw / w;
            double hn = bh / h;
            xn = Math.max(0.0, Math.min(1.0, xn));
            yn = Math.max(0.0, Math.min(1.0, yn));
            wn = Math.max(0.0, Math.min(1.0 - xn, wn));
            hn = Math.max(0.0, Math.min(1.0 - yn, hn));

            faces.add(new DetectedFace(xn, yn, wn, hn, probFace));
        }
        return faces;
    }

    /**
     * 在两套解码结果中选择更合理的一套：
     * - 优先选择中位数面积在 [0.01, 0.4] 之间的结果
     * - 如都不满足，选择中位数面积较大的那一套
     */
    private List<DetectedFace> chooseDecodedResult(List<DetectedFace> prior, List<DetectedFace> absolute,
                                                   List<DetectedFace> normalized, List<DetectedFace> absStretch,
                                                   List<DetectedFace> normStretch) {
        FaceSetScore priorScore = evaluateFaces(prior);
        FaceSetScore absScore = evaluateFaces(absolute);
        FaceSetScore normScore = evaluateFaces(normalized);
        FaceSetScore absStretchScore = evaluateFaces(absStretch);
        FaceSetScore normStretchScore = evaluateFaces(normStretch);

        if (isVerboseLog()) {
            log.debug("解码评分: prior area={} score={} count={} mx={} mw={}, abs area={} score={} count={} mx={} mw={}, norm area={} score={} count={} mx={} mw={}, absStretch area={} score={} count={} mx={} mw={}, normStretch area={} score={} count={} mx={} mw={}",
                    format4(priorScore.medianArea), priorScore.score, prior.size(), format4(priorScore.medianX), format4(priorScore.medianW),
                    format4(absScore.medianArea), absScore.score, absolute.size(), format4(absScore.medianX), format4(absScore.medianW),
                    format4(normScore.medianArea), normScore.score, normalized.size(), format4(normScore.medianX), format4(normScore.medianW),
                    format4(absStretchScore.medianArea), absStretchScore.score, absStretch.size(), format4(absStretchScore.medianX), format4(absStretchScore.medianW),
                    format4(normStretchScore.medianArea), normStretchScore.score, normStretch.size(), format4(normStretchScore.medianX), format4(normStretchScore.medianW));
        }

        List<FaceSetScore> scores = List.of(priorScore, absScore, normScore, absStretchScore, normStretchScore);
        List<List<DetectedFace>> sets = List.of(prior, absolute, normalized, absStretch, normStretch);

        // 先按评分
        int bestScore = scores.stream().mapToInt(s -> s.score).max().orElse(0);
        if (bestScore > 0) {
            double bestGap = Double.MAX_VALUE;
            List<DetectedFace> bestSet = prior;
            for (int i = 0; i < scores.size(); i++) {
                if (scores.get(i).score != bestScore) continue;
                // 过滤明显偏移：中位x接近0或w极小
                if (scores.get(i).medianX < 0.01 || scores.get(i).medianW < 0.01) continue;
                double m = scores.get(i).medianArea;
                double targetMin = 0.05, targetMax = 0.3;
                double gap = (m < targetMin) ? (targetMin - m) : (m > targetMax ? m - targetMax : 0);
                if (gap < bestGap) {
                    bestGap = gap;
                    bestSet = sets.get(i);
                }
            }
            return bestSet;
        }

        // 若评分都为0，选中位数面积较大且中位x不贴边的
        double bestMedian = -1;
        List<DetectedFace> bestSet = prior;
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i).medianX < 0.01 || scores.get(i).medianW < 0.01) continue;
            double m = scores.get(i).medianArea;
            if (m > bestMedian) {
                bestMedian = m;
                bestSet = sets.get(i);
            }
        }
        return bestSet;
    }

    /**
     * 简单评分：中位数面积在 [0.01,0.4] 记为2；在[0.001,0.8]记为1；否则0
     */
    private FaceSetScore evaluateFaces(List<DetectedFace> faces) {
        if (faces == null || faces.isEmpty()) return new FaceSetScore(0, 0, 0, 0);
        List<Double> areas = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        for (DetectedFace f : faces) {
            areas.add(f.getWidth() * f.getHeight());
            xs.add(f.getX());
            ws.add(f.getWidth());
        }
        areas.sort(Double::compareTo);
        xs.sort(Double::compareTo);
        ws.sort(Double::compareTo);
        double median = areas.get(areas.size() / 2);
        double medianX = xs.get(xs.size() / 2);
        double medianW = ws.get(ws.size() / 2);
        int score = 0;
        if (median >= 0.01 && median <= 0.4) score = 2;
        else if (median >= 0.001 && median <= 0.8) score = 1;
        return new FaceSetScore(score, median, medianX, medianW);
    }

    private String format4(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    private static class FaceSetScore {
        int score;
        double medianArea;
        double medianX;
        double medianW;

        FaceSetScore(int score, double medianArea, double medianX, double medianW) {
            this.score = score;
            this.medianArea = medianArea;
            this.medianX = medianX;
            this.medianW = medianW;
        }
    }
    /**
     * 旧格式解析：boxes=[1,N,4]，scores=[1,N]
     */
    private List<DetectedFace> parseDetections(float[][][] boxes, float[][] scores, 
                                               int originalWidth, int originalHeight, int inputSize) {
        List<DetectedFace> faces = new ArrayList<>();
        
        if (boxes.length == 0 || scores.length == 0) {
            return faces;
        }
        
        // 计算缩放比例（模型输入尺寸到原始图片尺寸）
        double scaleX = (double) originalWidth / inputSize;
        double scaleY = (double) originalHeight / inputSize;
        
        // 解析检测结果（RetinaFace格式：每个检测框有4个坐标）
        for (int i = 0; i < scores[0].length; i++) {
            float score = scores[0][i];
            if (score < confidenceThreshold) {
                continue;
            }
            
            if (boxes[0][i].length >= 4) {
                // 坐标格式可能是 [x1, y1, x2, y2] 或 [cx, cy, w, h]
                float x1 = boxes[0][i][0];
                float y1 = boxes[0][i][1];
                float x2 = boxes[0][i][2];
                float y2 = boxes[0][i][3];
                
                // 转换为归一化坐标
                double x = (x1 / inputSize) * scaleX / originalWidth;
                double y = (y1 / inputSize) * scaleY / originalHeight;
                double w = ((x2 - x1) / inputSize) * scaleX / originalWidth;
                double h = ((y2 - y1) / inputSize) * scaleY / originalHeight;
                
                // 确保坐标在有效范围内
                x = Math.max(0.0, Math.min(1.0, x));
                y = Math.max(0.0, Math.min(1.0, y));
                w = Math.max(0.01, Math.min(1.0 - x, w));
                h = Math.max(0.01, Math.min(1.0 - y, h));
                
                faces.add(new DetectedFace(x, y, w, h, score));
            }
        }
        
        return faces;
    }

    private List<DetectedFace> applyNMS(List<DetectedFace> faces) {
        if (faces.size() <= 1) {
            return faces;
        }

        // 按置信度降序排序，并限制参与 NMS 的最大候选数，避免在低阈值时过多框导致性能问题
        faces.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        if (faces.size() > MAX_NMS_CANDIDATES) {
            faces = new ArrayList<>(faces.subList(0, MAX_NMS_CANDIDATES));
        }

        List<DetectedFace> result = new ArrayList<>();
        boolean[] suppressed = new boolean[faces.size()];

        for (int i = 0; i < faces.size(); i++) {
            if (suppressed[i]) continue;

            DetectedFace face1 = faces.get(i);
            result.add(face1);

            for (int j = i + 1; j < faces.size(); j++) {
                if (suppressed[j]) continue;

                DetectedFace face2 = faces.get(j);
                double iou = calculateIOU(face1, face2);
                if (iou > nmsThreshold) {
                    suppressed[j] = true;
                }
            }
        }

        return result;
    }

    private double calculateIOU(DetectedFace f1, DetectedFace f2) {
        double x1 = Math.max(f1.getX(), f2.getX());
        double y1 = Math.max(f1.getY(), f2.getY());
        double x2 = Math.min(f1.getX() + f1.getWidth(), f2.getX() + f2.getWidth());
        double y2 = Math.min(f1.getY() + f1.getHeight(), f2.getY() + f2.getHeight());
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0;
        }
        
        double intersection = (x2 - x1) * (y2 - y1);
        double area1 = f1.getWidth() * f1.getHeight();
        double area2 = f2.getWidth() * f2.getHeight();
        double union = area1 + area2 - intersection;
        
        return union > 0 ? intersection / union : 0.0;
    }

    /**
     * 记录系统诊断信息
     */
    private void logSystemInfo() {
        try {
            // Java版本信息
            log.warn("Java版本: {}", System.getProperty("java.version"));
            log.warn("Java供应商: {}", System.getProperty("java.vendor"));
            log.warn("Java主目录: {}", System.getProperty("java.home"));

            // 操作系统信息
            log.warn("操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
            log.warn("系统架构: {}", System.getProperty("os.arch"));

            // JVM信息
            log.warn("JVM名称: {}", System.getProperty("java.vm.name"));
            log.warn("JVM版本: {}", System.getProperty("java.vm.version"));

            // 用户信息
            log.warn("用户目录: {}", System.getProperty("user.dir"));
            log.warn("用户主目录: {}", System.getProperty("user.home"));
            log.warn("文件分隔符: {}", System.getProperty("file.separator"));
            log.warn("路径分隔符: {}", System.getProperty("path.separator"));

            // 库路径
            String javaLibPath = System.getProperty("java.library.path");
            log.warn("Java库路径: {}", javaLibPath != null ? javaLibPath.replace(System.getProperty("path.separator"), "\n  ") : "null");

            // 检查ONNX Runtime JAR
            try {
                Class<?> clazz = Class.forName("ai.onnxruntime.OrtEnvironment");
                log.warn("ONNX Runtime类加载成功: {}", clazz.getProtectionDomain().getCodeSource().getLocation());
            } catch (ClassNotFoundException cnfe) {
                log.warn("ONNX Runtime类未找到: {}", cnfe.getMessage());
            } catch (Exception ex) {
                log.warn("ONNX Runtime类状态异常: {}", ex.getMessage());
            }

        } catch (Exception e) {
            log.warn("收集系统信息时出错: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (detectionSession != null) detectionSession.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
    }

    public static class DetectedFace {
        private double x;
        private double y;
        private double width;
        private double height;
        private double confidence;

        public DetectedFace(double x, double y, double width, double height, double confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public double getConfidence() { return confidence; }
    }
}

