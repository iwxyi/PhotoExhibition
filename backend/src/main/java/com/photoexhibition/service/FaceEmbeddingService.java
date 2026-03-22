package com.photoexhibition.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.photoexhibition.entity.Face;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaceEmbeddingService implements AutoCloseable {

    private static final int MODEL_INPUT_SIZE = 112;
    private static final double CROP_MARGIN_RATIO = 0.18;
    private static final double REDETECT_MARGIN_RATIO = 0.22;

    private final FaceRecognitionService faceRecognitionService;

    @Value("${face.embedding.model-path:./models/face_recognition.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;

    public float[] extract(File imageFile, Face face) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) return null;
            return extractFromImage(img, face);
        } catch (Exception e) {
            log.warn("人脸向量提取失败: {}", imageFile.getName(), e);
        }
        return null;
    }

    public float[] extractFromImage(BufferedImage img, Face face) {
        try {
            ensureSession();
            if (session == null) return null;

            BufferedImage crop = cropFace(img, face);
            if (crop == null) return null;

            BufferedImage normalized = normalizeFaceOrientation(crop);
            BufferedImage prepared = resizeLetterbox(normalized, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);
            float[] tensor = toCHW(prepared);

            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor),
                new long[]{1, 3, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE});
            try (OrtSession.Result result = session.run(
                Collections.singletonMap(session.getInputNames().iterator().next(), input))) {
                Object out = result.get(0).getValue();
                if (out instanceof float[][]) {
                    float[] vec = ((float[][]) out)[0];
                    normalize(vec);
                    return vec;
                }
            } finally {
                input.close();
            }
        } catch (Exception e) {
            log.warn("人脸向量提取失败", e);
        }
        return null;
    }

    private void ensureSession() {
        if (session != null) return;
        try {
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelPath, new OrtSession.SessionOptions());
            log.info("人脸特征模型已加载: {}", modelPath);
        } catch (Exception e) {
            log.warn("加载人脸特征模型失败: {}", modelPath, e);
            session = null;
        }
    }

    private BufferedImage cropFace(BufferedImage img, Face face) {
        if (face.getX() == null || face.getY() == null || face.getWidth() == null || face.getHeight() == null) {
            return img;
        }
        int x = (int) Math.round(face.getX() * img.getWidth());
        int y = (int) Math.round(face.getY() * img.getHeight());
        int w = (int) Math.round(face.getWidth() * img.getWidth());
        int h = (int) Math.round(face.getHeight() * img.getHeight());

        x = Math.max(0, x);
        y = Math.max(0, y);
        w = Math.max(1, Math.min(w, img.getWidth() - x));
        h = Math.max(1, Math.min(h, img.getHeight() - y));

        int marginX = (int) Math.round(w * CROP_MARGIN_RATIO);
        int marginY = (int) Math.round(h * CROP_MARGIN_RATIO);
        x = Math.max(0, x - marginX);
        y = Math.max(0, y - marginY);
        w = Math.max(1, Math.min(img.getWidth() - x, w + marginX * 2));
        h = Math.max(1, Math.min(img.getHeight() - y, h + marginY * 2));

        try {
            return img.getSubimage(x, y, w, h);
        } catch (RasterFormatException e) {
            log.warn("裁剪人脸越界，使用整图: {}", img);
            return img;
        }
    }

    private BufferedImage normalizeFaceOrientation(BufferedImage crop) {
        if (crop == null) {
            return null;
        }

        BufferedImage bestImage = crop;
        double bestScore = -1;

        for (int rotation : new int[]{0, 90, 270, 180}) {
            BufferedImage rotated = rotate(crop, rotation);
            FaceCandidate candidate = detectBestFace(rotated);
            if (candidate == null) {
                continue;
            }

            if (candidate.score > bestScore) {
                bestScore = candidate.score;
                bestImage = expandAndCrop(rotated, candidate.face);
            }
        }

        return bestImage;
    }

    private FaceCandidate detectBestFace(BufferedImage image) {
        List<FaceRecognitionService.DetectedFace> detectedFaces = faceRecognitionService.detectFaces(image);
        if (detectedFaces == null || detectedFaces.isEmpty()) {
            return null;
        }

        FaceRecognitionService.DetectedFace bestFace = null;
        double bestScore = -1;
        for (FaceRecognitionService.DetectedFace detectedFace : detectedFaces) {
            double area = detectedFace.getWidth() * detectedFace.getHeight();
            double centerX = detectedFace.getX() + detectedFace.getWidth() / 2.0;
            double centerY = detectedFace.getY() + detectedFace.getHeight() / 2.0;
            double centerDistance = Math.abs(centerX - 0.5) + Math.abs(centerY - 0.5);
            double score = detectedFace.getConfidence() * 0.65 + area * 0.25 + (1.0 - centerDistance) * 0.10;
            if (score > bestScore) {
                bestScore = score;
                bestFace = detectedFace;
            }
        }

        return bestFace == null ? null : new FaceCandidate(bestFace, bestScore);
    }

    private BufferedImage expandAndCrop(BufferedImage image, FaceRecognitionService.DetectedFace face) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int x = (int) Math.round(face.getX() * imageWidth);
        int y = (int) Math.round(face.getY() * imageHeight);
        int w = (int) Math.round(face.getWidth() * imageWidth);
        int h = (int) Math.round(face.getHeight() * imageHeight);

        int marginX = (int) Math.round(w * REDETECT_MARGIN_RATIO);
        int marginY = (int) Math.round(h * REDETECT_MARGIN_RATIO);
        x = Math.max(0, x - marginX);
        y = Math.max(0, y - marginY);
        w = Math.max(1, Math.min(imageWidth - x, w + marginX * 2));
        h = Math.max(1, Math.min(imageHeight - y, h + marginY * 2));

        try {
            return image.getSubimage(x, y, w, h);
        } catch (RasterFormatException e) {
            return image;
        }
    }

    private BufferedImage resizeLetterbox(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = out.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, w, h);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double scale = Math.min((double) w / src.getWidth(), (double) h / src.getHeight());
        int drawWidth = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int drawHeight = Math.max(1, (int) Math.round(src.getHeight() * scale));
        int offsetX = (w - drawWidth) / 2;
        int offsetY = (h - drawHeight) / 2;
        g2d.drawImage(src, offsetX, offsetY, drawWidth, drawHeight, null);
        g2d.dispose();
        return out;
    }

    private BufferedImage rotate(BufferedImage src, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) {
            return src;
        }

        int newWidth = normalized == 90 || normalized == 270 ? src.getHeight() : src.getWidth();
        int newHeight = normalized == 90 || normalized == 270 ? src.getWidth() : src.getHeight();

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rotated.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, newWidth, newHeight);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.translate(newWidth / 2.0, newHeight / 2.0);
        g2d.rotate(Math.toRadians(normalized));
        g2d.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    private float[] toCHW(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        float[] out = new float[3 * w * h];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                out[idx] = (b - 127.5f) / 128f;
                out[idx + w * h] = (g - 127.5f) / 128f;
                out[idx + 2 * w * h] = (r - 127.5f) / 128f;
                idx++;
            }
        }
        return out;
    }

    private void normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm < 1e-6) return;
        for (int i = 0; i < v.length; i++) {
            v[i] /= (float) norm;
        }
    }

    private static class FaceCandidate {
        final FaceRecognitionService.DetectedFace face;
        final double score;

        FaceCandidate(FaceRecognitionService.DetectedFace face, double score) {
            this.face = face;
            this.score = score;
        }
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
