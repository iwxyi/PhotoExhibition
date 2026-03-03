package com.photoexhibition.service;

import com.photoexhibition.entity.Face;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ai.onnxruntime.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.awt.image.RasterFormatException;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

@Service
@Slf4j
public class FaceEmbeddingService implements AutoCloseable {

    @Value("${face.embedding.model-path:./models/face_recognition.onnx}")
    private String modelPath;

    private OrtEnvironment env;
    private OrtSession session;

    /**
     * 提取人脸向量（失败返回 null）
     */
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

    /**
     * 从已加载的图片提取人脸向量（避免重复磁盘 I/O）
     */
    public float[] extractFromImage(BufferedImage img, Face face) {
        try {
            ensureSession();
            if (session == null) return null;

            BufferedImage crop = cropFace(img, face);
            if (crop == null) return null;

            BufferedImage resized = resize(crop, 112, 112);
            float[] tensor = toCHW(resized);

            OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), new long[]{1, 3, 112, 112});
            try (OrtSession.Result result = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), input))) {
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

        try {
            return img.getSubimage(x, y, w, h);
        } catch (RasterFormatException e) {
            log.warn("裁剪人脸越界，使用整图: {}", img);
            return img;
        }
    }

    private BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = out.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, w, h, null);
        g2d.dispose();
        return out;
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
                // ArcFace / InsightFace 通常使用 BGR 输入，减 127.5 并除以 128
                out[idx] = (b - 127.5f) / 128f;           // B
                out[idx + w * h] = (g - 127.5f) / 128f;   // G
                out[idx + 2 * w * h] = (r - 127.5f) / 128f; // R
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

    @Override
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception ignored) {
        }
    }
}

