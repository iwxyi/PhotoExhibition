package com.photoexhibition;

/**
 * 快门速度格式化测试
 */
public class ShutterSpeedTest {
    public static void main(String[] args) {
        testShutterSpeedFormatting();
    }

    /**
     * 将快门秒数转换为分数形式显示
     */
    private static String formatShutterSpeedFromSeconds(Double seconds) {
        if (seconds == null || seconds == 0) return "0";
        if (seconds >= 1) return String.valueOf(Math.round(seconds));  // 超出1秒显示整数
        if (seconds >= 0.1) return String.format("%.1f", seconds);  // 0.1秒到1秒之间显示小数
        // 小于一秒显示倒数，分母取整
        int denominator = (int) Math.round(1.0 / seconds);
        return "1/" + denominator;
    }

    private static void testShutterSpeedFormatting() {
        double[] testValues = {
            0.06666666666666667, // 1/15
            0.5,                  // 1/2
            2.0,                  // 2
            0.25,                 // 1/4
            0.125,                // 1/8
            0.03333333333333333, // 1/30
            1.5,                  // 2 (四舍五入)
            0.1,                  // 0.1
            0.05,                 // 1/20
            0.0667,               // 1/15 (近似)
            0.04,                 // 1/25
            0.002,                // 1/500
            0.001,                // 1/1000
        };

        System.out.println("快门速度格式化测试:");
        System.out.println("输入值 -> 输出结果");
        System.out.println("-------------------");

        for (double value : testValues) {
            String formatted = formatShutterSpeedFromSeconds(value);
            System.out.printf("  %.15f -> %s%n", value, formatted);
        }
    }
}
