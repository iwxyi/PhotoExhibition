public class TestOnnx {
    public static void main(String[] args) {
        try {
            System.out.println("Testing ONNX Runtime initialization...");
            ai.onnxruntime.OrtEnvironment env = ai.onnxruntime.OrtEnvironment.getEnvironment();
            System.out.println("ONNX Runtime initialized successfully!");
            env.close();
        } catch (Exception e) {
            System.err.println("ONNX Runtime initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
