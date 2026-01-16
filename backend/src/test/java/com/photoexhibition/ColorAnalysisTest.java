package com.photoexhibition;

import com.photoexhibition.service.ColorAnalysisService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ColorAnalysisTest {

    @Test
    public void testColorPaletteExtraction() {
        ColorAnalysisService service = new ColorAnalysisService();

        // 使用一个测试图片
        File testImage = new File("data/photos/风景/2023.08.23 梦想小镇/IMG_4639.jpeg");

        if (testImage.exists()) {
            try {
                BufferedImage image = ImageIO.read(testImage);
                BufferedImage scaledImage = org.imgscalr.Scalr.resize(image, org.imgscalr.Scalr.Method.QUALITY, 200);

                List<Color> palette = service.extractColorPalette(scaledImage, 6);

                System.out.println("提取的调色板颜色数量: " + palette.size());
                for (int i = 0; i < palette.size(); i++) {
                    Color color = palette.get(i);
                    String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                    System.out.println("颜色 " + (i + 1) + ": " + hex);
                }

                // 检查颜色多样性
                boolean hasSimilarColors = checkColorSimilarity(palette);
                System.out.println("是否存在相似颜色: " + hasSimilarColors);

            } catch (IOException e) {
                System.err.println("读取图片失败: " + e.getMessage());
            }
        } else {
            System.out.println("测试图片不存在: " + testImage.getAbsolutePath());
        }
    }

    private boolean checkColorSimilarity(List<Color> colors) {
        for (int i = 0; i < colors.size(); i++) {
            for (int j = i + 1; j < colors.size(); j++) {
                Color c1 = colors.get(i);
                Color c2 = colors.get(j);

                // 计算颜色距离
                int dr = c1.getRed() - c2.getRed();
                int dg = c1.getGreen() - c2.getGreen();
                int db = c1.getBlue() - c2.getBlue();
                double distance = Math.sqrt(dr * dr + dg * dg + db * db);

                // 如果距离小于50，认为颜色相似
                if (distance < 50) {
                    System.out.println("发现相似颜色: " +
                        String.format("#%02x%02x%02x", c1.getRed(), c1.getGreen(), c1.getBlue()) +
                        " 和 " +
                        String.format("#%02x%02x%02x", c2.getRed(), c2.getGreen(), c2.getBlue()) +
                        " (距离: " + String.format("%.2f", distance) + ")");
                    return true;
                }
            }
        }
        return false;
    }
}
