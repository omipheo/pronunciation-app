# ONNX Runtime uses JNI + reflection into these classes.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
