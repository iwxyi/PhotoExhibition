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

    /**
     * 使用ONNX模型检测人脸
     * 如果模型未配置或加载失败，返回空列表（会回退到简单检测方法）
     */
    public List<DetectedFace> detectFaces(File imageFile) {
        if (!enabled) {
            return new ArrayList<>();
        }

        try {
            ensureSession();
            if (detectionSession == null) {
                return new ArrayList<>();
            }

            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
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
                // 调试输出：打印所有输出的名称、类型和shape，便于适配不同模型
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

                // 解析输出（当前假定RetinaFace风格输出格式：bbox, confidence, landmark）
                if (result.size() >= 2) {
                    Object boxesObj = result.get(0).getValue();
                    Object scoresObj = result.get(1).getValue();

                    log.debug("人脸检测ONNX原始输出类型: boxes={}, scores={}",
                            boxesObj != null ? boxesObj.getClass().getName() : "null",
                            scoresObj != null ? scoresObj.getClass().getName() : "null");

                    // 格式一：boxes=[1,N,4]，scores=[1,N,2]（常见RetinaFace实现）
                    if (boxesObj instanceof float[][][] && scoresObj instanceof float[][][]) {
                        float[][][] boxes = (float[][][]) boxesObj;
                        float[][][] scores = (float[][][]) scoresObj;

                        List<DetectedFace> faces = parseDetectionsRetinaStyle(boxes, scores, img.getWidth(), img.getHeight(), size);
                        log.debug("人脸检测ONNX解析得到 {} 个候选框（解析前未做NMS）", faces.size());
                        return applyNMS(faces);
                    }
                    // 旧格式：boxes=[1,N,4]，scores=[1,N]（如果以后换成这种，可以继续复用）
                    if (boxesObj instanceof float[][][] && scoresObj instanceof float[][]) {
                        float[][][] boxes = (float[][][]) boxesObj;
                        float[][] scores = (float[][]) scoresObj;

                        List<DetectedFace> faces = parseDetections(boxes, scores, img.getWidth(), img.getHeight(), size);
                        log.debug("人脸检测ONNX解析得到 {} 个候选框（解析前未做NMS，旧格式）", faces.size());
                        return applyNMS(faces);
                    } else {
                        // 如果类型不匹配，先只记录日志，后续根据日志调整解析逻辑
                        log.warn("人脸检测ONNX输出格式与当前解析逻辑不匹配，暂不返回检测结果。请根据日志中的shape/类型调整解析代码。");
                    }
                } else {
                    log.warn("人脸检测ONNX返回的输出数量不足2个，当前解析逻辑无法处理。输出个数={}", result.size());
                }
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("ONNX人脸检测失败，将回退到简单检测方法: {}", imageFile.getName(), e);
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
            
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            // 可以根据需要设置线程数等选项
            detectionSession = env.createSession(detectionModelPath, opts);
            log.info("人脸检测模型已加载: {}", detectionModelPath);
        } catch (Exception e) {
            log.warn("加载人脸检测模型失败: {}，将使用简单检测方法", detectionModelPath, e);
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
        
        java.awt.Image scaled = img.getScaledInstance(newW, newH, java.awt.Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, targetSize, targetSize);
        g.drawImage(scaled, (targetSize - newW) / 2, (targetSize - newH) / 2, null);
        g.dispose();
        
        return resized;
    }

    private float[] preprocessImage(BufferedImage img, int size) {
        float[] tensor = new float[3 * size * size];
        int idx = 0;
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                
                // 归一化到[0, 1]或[-1, 1]，根据模型要求调整
                tensor[idx] = (r - 127.5f) / 128f;
                tensor[idx + size * size] = (g - 127.5f) / 128f;
                tensor[idx + 2 * size * size] = (b - 127.5f) / 128f;
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

        log.debug("人脸检测ONNX（Retina风格）通过置信度阈值的候选框数量: {}/{}，最终保留（含几何过滤）: {}，最大人脸概率={}，阈值={}",
                passedScoreCount, numAnchors, validCount,
                String.format(java.util.Locale.ROOT, "%.4f", maxProb),
                String.format(java.util.Locale.ROOT, "%.4f", effectiveThreshold));

        // 打印 TOP-K 调试信息，帮助分析坐标缩放是否正确
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

        if (validCount == 0) {
            log.debug("人脸检测ONNX：在当前阈值下未保留任何候选框，建议暂时调低 face.detection.confidence-threshold 以观察效果。");
        }
        return faces;
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

