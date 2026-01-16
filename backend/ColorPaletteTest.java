import com.photoexhibition.service.ColorAnalysisService;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ColorPaletteTest {

    public static void main(String[] args) {
        ColorAnalysisService service = new ColorAnalysisService();

        // 使用一个测试图片
        File testImage = new File("data/photos/风景/2023.08.23 梦想小镇/IMG_4639.jpeg");

        if (testImage.exists()) {
            try {
                System.out.println("测试图片: " + testImage.getAbsolutePath());

                BufferedImage image = ImageIO.read(testImage);
                System.out.println("原始图片尺寸: " + image.getWidth() + "x" + image.getHeight());

                // 简单缩放图片
                BufferedImage scaledImage = scaleImage(image, 200);
                System.out.println("缩放后图片尺寸: " + scaledImage.getWidth() + "x" + scaledImage.getHeight());

                List<Color> palette = service.extractColorPalette(scaledImage, 6);

                System.out.println("\n=== 提取的调色板颜色 ===");
                System.out.println("颜色数量: " + palette.size());

                for (int i = 0; i < palette.size(); i++) {
                    Color color = palette.get(i);
                    String hex = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                    float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                    System.out.printf("颜色 %d: %s (RGB: %d,%d,%d) (HSB: %.1f°, %.1f%%, %.1f%%)%n",
                        i + 1, hex,
                        color.getRed(), color.getGreen(), color.getBlue(),
                        hsb[0] * 360, hsb[1] * 100, hsb[2] * 100);
                }

                // 检查颜色多样性
                System.out.println("\n=== 颜色相似性检查 ===");
                boolean hasSimilarColors = checkColorSimilarity(palette);
                System.out.println("是否存在相似颜色: " + hasSimilarColors);

                // 计算颜色分布统计
                System.out.println("\n=== 颜色统计 ===");
                printColorStatistics(palette);

            } catch (IOException e) {
                System.err.println("读取图片失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("测试图片不存在: " + testImage.getAbsolutePath());
            System.out.println("当前目录: " + new File(".").getAbsolutePath());

            // 列出可用的测试图片
            System.out.println("\n可用的测试图片:");
            listAvailableImages(new File("data/photos"));
        }
    }

    private static boolean checkColorSimilarity(List<Color> colors) {
        boolean foundSimilar = false;
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
                    String hex1 = String.format("#%02x%02x%02x", c1.getRed(), c1.getGreen(), c1.getBlue());
                    String hex2 = String.format("#%02x%02x%02x", c2.getRed(), c2.getGreen(), c2.getBlue());
                    System.out.printf("发现相似颜色: %s 和 %s (距离: %.2f)%n", hex1, hex2, distance);
                    foundSimilar = true;
                }
            }
        }
        if (!foundSimilar) {
            System.out.println("所有颜色都足够不同，没有发现相似颜色");
        }
        return foundSimilar;
    }

    private static void printColorStatistics(List<Color> colors) {
        // 计算平均颜色
        double avgR = colors.stream().mapToInt(Color::getRed).average().orElse(0);
        double avgG = colors.stream().mapToInt(Color::getGreen).average().orElse(0);
        double avgB = colors.stream().mapToInt(Color::getBlue).average().orElse(0);

        String avgHex = String.format("#%02x%02x%02x", (int)avgR, (int)avgG, (int)avgB);
        System.out.printf("平均颜色: %s (RGB: %.1f,%.1f,%.1f)%n", avgHex, avgR, avgG, avgB);

        // 计算颜色范围
        int minR = colors.stream().mapToInt(Color::getRed).min().orElse(0);
        int maxR = colors.stream().mapToInt(Color::getRed).max().orElse(0);
        int minG = colors.stream().mapToInt(Color::getGreen).min().orElse(0);
        int maxG = colors.stream().mapToInt(Color::getGreen).max().orElse(0);
        int minB = colors.stream().mapToInt(Color::getBlue).min().orElse(0);
        int maxB = colors.stream().mapToInt(Color::getBlue).max().orElse(0);

        System.out.printf("红色范围: %d-%d%n", minR, maxR);
        System.out.printf("绿色范围: %d-%d%n", minG, maxG);
        System.out.printf("蓝色范围: %d-%d%n", minB, maxB);
    }

    private static void listAvailableImages(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        File[] images = file.listFiles((f, name) ->
                            name.toLowerCase().endsWith(".jpg") ||
                            name.toLowerCase().endsWith(".jpeg") ||
                            name.toLowerCase().endsWith(".png"));
                        if (images != null && images.length > 0) {
                            System.out.println("目录: " + file.getPath() + " (" + images.length + " 张图片)");
                            for (int i = 0; i < Math.min(3, images.length); i++) {
                                System.out.println("  - " + images[i].getName());
                            }
                            if (images.length > 3) {
                                System.out.println("  ... 还有 " + (images.length - 3) + " 张");
                            }
                        }
                    }
                }
            }
        }
    }

    // 简单的图片缩放方法
    private static BufferedImage scaleImage(BufferedImage originalImage, int targetSize) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 计算缩放比例，保持宽高比
        double scale = (double) targetSize / Math.max(originalWidth, originalHeight);

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // 创建缩放后的图片
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, originalImage.getType());
        Graphics2D g2d = scaledImage.createGraphics();

        // 设置渲染提示以获得更好的质量
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return scaledImage;
    }
}
