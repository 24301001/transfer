# YOLOv5 FastAPI Inference Service

本服务基于 FastAPI + YOLOv5 + OpenCV，提供图片、视频文件和实时视频流检测接口。

## 项目目录结构

```text
yolov5-master/
├── inference_api.py          # FastAPI 服务入口
├── requirements_api.txt      # API 额外依赖
├── weights/
│   └── best.pt               # 默认自定义权重路径
├── models/
├── utils/
├── train.py
├── detect.py
└── runs/
    └── api/
        ├── images/           # 图片检测结果
        ├── videos/           # 视频检测结果
        └── uploads/          # 上传视频临时文件
```

## best.pt 放哪里

默认权重路径是：

```text
weights/best.pt
```

如果该文件不存在，`GET /health` 会返回 `model_loaded: false` 和清晰的错误信息，检测接口会返回 500 错误。

当前类别：

```python
['car damage', 'fire', 'car flip', 'car', 'car crash']
```

## 安装 API 依赖

先安装 YOLOv5 原始依赖，再安装 API 额外依赖：

```bash
pip install -r requirements.txt
pip install -r requirements_api.txt
```

## 启动服务

在项目根目录执行：

```bash
uvicorn inference_api:app --host 0.0.0.0 --port 8000
```

健康检查：

```bash
curl http://127.0.0.1:8000/health
```

## 图片检测调用

接口：

```text
POST /predict/image
```

参数：

- `file`: 上传图片文件
- `conf`: 置信度阈值，默认 `0.25`
- `iou`: NMS IoU 阈值，默认 `0.45`
- `imgsz`: 推理尺寸，默认 `640`

curl 示例：

```bash
curl -X POST "http://127.0.0.1:8000/predict/image?conf=0.25" \
  -F "file=@test.jpg"
```

返回的 `output_image_url` 可以直接访问，例如：

```text
http://127.0.0.1:8000/runs/api/images/xxx.jpg
```

## 视频检测调用

接口：

```text
POST /predict/video
```

参数：

- `file`: 上传视频文件
- `conf`: 置信度阈值，默认 `0.25`
- `iou`: NMS IoU 阈值，默认 `0.45`
- `imgsz`: 推理尺寸，默认 `640`

curl 示例：

```bash
curl -X POST "http://127.0.0.1:8000/predict/video?conf=0.25" \
  -F "file=@demo.mp4"
```

处理后视频保存在：

```text
runs/api/videos/
```

返回的 `output_video_url` 可以直接访问，例如：

```text
http://127.0.0.1:8000/runs/api/videos/xxx_detected.mp4
```

## 实时视频流如何打开

接口：

```text
GET /predict/stream
```

本机摄像头：

```text
http://127.0.0.1:8000/predict/stream?source=0&conf=0.25
```

RTSP 或 HTTP 视频流：

```text
http://127.0.0.1:8000/predict/stream?source=rtsp://user:password@host:554/stream&conf=0.25
```

前端可直接使用：

```html
<img src="http://127.0.0.1:8000/predict/stream?source=0&conf=0.25" />
```

## 返回 JSON 示例

图片检测：

```json
{
  "success": true,
  "filename": "test.jpg",
  "image_shape": {
    "height": 720,
    "width": 1280
  },
  "conf": 0.25,
  "detections": [
    {
      "class_id": 3,
      "class_name": "car",
      "confidence": 0.914235,
      "bbox": [120.0, 88.0, 520.0, 360.0]
    }
  ],
  "detection_count": 1,
  "output_image_path": "E:/.../runs/api/images/test_12345678.jpg",
  "output_image_url": "/runs/api/images/test_12345678.jpg"
}
```

视频检测：

```json
{
  "success": true,
  "filename": "demo.mp4",
  "conf": 0.25,
  "output_video_path": "E:/.../runs/api/videos/demo_12345678_detected.mp4",
  "output_video_url": "/runs/api/videos/demo_12345678_detected.mp4",
  "stats": {
    "frames_reported_by_video": 300,
    "frames_processed": 300,
    "fps": 25.0,
    "width": 1280,
    "height": 720,
    "total_detections": 42,
    "per_class": {
      "car damage": 3,
      "fire": 1,
      "car flip": 0,
      "car": 35,
      "car crash": 3
    }
  }
}
```
