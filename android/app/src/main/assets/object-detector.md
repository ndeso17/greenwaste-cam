# Bundled location proposal model

Model: YOLOv10n, COCO, ONNX Community (Xenova HF Staff), original Ultralytics export.
Source: https://huggingface.co/onnx-community/YOLOv10/blob/main/yolov10n.onnx
Download: https://huggingface.co/onnx-community/YOLOv10/resolve/main/yolov10n.onnx
SHA-256: a77dd863933f184a19e84361c64b788228a7c7dacc2c78939239a96ad3efca3b
License declared by publisher/model metadata: AGPL-3.0.
Retrieved: 27 October 2026. No model training performed.
Input: images float32 [1,3,640,640], RGB /255 with letterbox.
Output: output0 [1,300,6], xyxy / score / COCO class.

The model supplies object locations only. The active ResNet18 supplies the
seven material classes; COCO labels do not replace the product classes.
People, chair/couch/bed/table/toilet proposals are excluded as scene background.
Other false positives and missed objects remain possible. COCO does not cover
all waste categories. This is a prototype location provider, not proof of
seven-class detector accuracy.

The earlier CV/best.onnx was evaluated but not bundled in the final version:
it produced one full-frame person box on the captured cup/bottle scene.
The replacement produced separate cup/bottle proposals on the same input.
