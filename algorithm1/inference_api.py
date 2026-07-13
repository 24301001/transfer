import os
import shutil
import sys
import time
import uuid
import pathlib
from pathlib import Path
from typing import Any, Dict, Generator, List, Optional

PROJECT_ROOT = Path(__file__).resolve().parent
YOLOV5_ROOT = PROJECT_ROOT / "yolov5"
if YOLOV5_ROOT.exists():
    sys.path.insert(0, str(YOLOV5_ROOT))
if os.name == "nt":
    pathlib.PosixPath = pathlib.WindowsPath
ULTRALYTICS_CONFIG_DIR = PROJECT_ROOT / ".ultralytics"
ULTRALYTICS_CONFIG_DIR.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("YOLO_CONFIG_DIR", str(ULTRALYTICS_CONFIG_DIR))

import cv2
import numpy as np
import torch
from fastapi import FastAPI, File, HTTPException, Query, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from ultralytics.utils.plotting import Annotator, colors

from models.common import DetectMultiBackend
from utils.augmentations import letterbox
from utils.general import check_img_size, non_max_suppression, scale_boxes
from utils.torch_utils import select_device


DEFAULT_WEIGHTS = PROJECT_ROOT / "model" / "best.pt"
DEFAULT_NAMES = ["car damage", "fire", "car flip", "car", "car crash"]
RUNS_DIR = PROJECT_ROOT / "runs" / "api"
IMAGE_DIR = RUNS_DIR / "images"
VIDEO_DIR = RUNS_DIR / "videos"
UPLOAD_DIR = RUNS_DIR / "uploads"

for directory in (IMAGE_DIR, VIDEO_DIR, UPLOAD_DIR):
    directory.mkdir(parents=True, exist_ok=True)


class Detection(BaseModel):
    class_id: int
    class_name: str
    confidence: float
    bbox: List[float]


class YoloV5Inference:
    def __init__(self, weights: Path = DEFAULT_WEIGHTS, imgsz: int = 640):
        if not weights.exists():
            raise FileNotFoundError(
                f"YOLOv5 weights not found: {weights}. "
                "Please put your trained best.pt at weights/best.pt or update DEFAULT_WEIGHTS."
            )

        self.weights = weights
        self.device = select_device("0" if torch.cuda.is_available() else "cpu")
        self.model = DetectMultiBackend(str(weights), device=self.device, dnn=False, fp16=False)
        self.stride = int(self.model.stride)
        self.names = self._normalize_names(getattr(self.model, "names", DEFAULT_NAMES))
        self.imgsz = check_img_size((imgsz, imgsz), s=self.stride)
        self.model.warmup(imgsz=(1, 3, self.imgsz[0], self.imgsz[1]))

    @staticmethod
    def _normalize_names(names: Any) -> Dict[int, str]:
        if isinstance(names, dict):
            return {int(k): str(v) for k, v in names.items()}
        if isinstance(names, (list, tuple)):
            return {i: str(name) for i, name in enumerate(names)}
        return {i: name for i, name in enumerate(DEFAULT_NAMES)}

    def health(self) -> Dict[str, Any]:
        return {
            "model_loaded": True,
            "weights": str(self.weights),
            "device": str(self.device),
            "cuda_available": torch.cuda.is_available(),
            "class_names": [self.names[i] for i in sorted(self.names)],
        }

    def predict_frame(
        self,
        frame_bgr: np.ndarray,
        conf_thres: float = 0.25,
        iou_thres: float = 0.45,
        max_det: int = 300,
        draw: bool = True,
    ) -> tuple[np.ndarray, List[Dict[str, Any]]]:
        if frame_bgr is None or frame_bgr.size == 0:
            raise ValueError("Empty image frame.")

        im0 = frame_bgr.copy()
        im = letterbox(im0, self.imgsz, stride=self.stride, auto=True)[0]
        im = im.transpose((2, 0, 1))[::-1]
        im = np.ascontiguousarray(im)
        im = torch.from_numpy(im).to(self.device)
        im = im.float() / 255.0
        if im.ndimension() == 3:
            im = im.unsqueeze(0)

        with torch.no_grad():
            pred = self.model(im, augment=False, visualize=False)
            pred = non_max_suppression(pred, conf_thres, iou_thres, max_det=max_det)

        detections: List[Dict[str, Any]] = []
        annotator = Annotator(im0, line_width=2, example=str(self.names)) if draw else None
        det = pred[0]

        if len(det):
            det[:, :4] = scale_boxes(im.shape[2:], det[:, :4], im0.shape).round()
            for *xyxy, conf, cls in reversed(det):
                class_id = int(cls)
                class_name = self.names.get(class_id, str(class_id))
                bbox = [float(x.item()) for x in xyxy]
                confidence = float(conf.item())
                detections.append(
                    {
                        "class_id": class_id,
                        "class_name": class_name,
                        "confidence": round(confidence, 6),
                        "bbox": bbox,
                    }
                )
                if annotator is not None:
                    label = f"{class_name} {confidence:.2f}"
                    annotator.box_label(xyxy, label, color=colors(class_id, True))

        annotated = annotator.result() if annotator is not None else im0
        return annotated, detections


app = FastAPI(title="YOLOv5 Inference API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount("/runs/api", StaticFiles(directory=str(RUNS_DIR)), name="api-runs")

model_service: Optional[YoloV5Inference] = None
model_error: Optional[str] = None


def ensure_dirs() -> None:
    for directory in (IMAGE_DIR, VIDEO_DIR, UPLOAD_DIR):
        directory.mkdir(parents=True, exist_ok=True)


def get_model() -> YoloV5Inference:
    global model_service, model_error
    if model_service is not None:
        return model_service

    try:
        model_service = YoloV5Inference(DEFAULT_WEIGHTS)
        model_error = None
        return model_service
    except Exception as exc:
        model_error = str(exc)
        raise HTTPException(status_code=500, detail=model_error) from exc


@app.on_event("startup")
def startup() -> None:
    global model_service, model_error
    ensure_dirs()
    try:
        model_service = YoloV5Inference(DEFAULT_WEIGHTS)
        model_error = None
    except Exception as exc:
        model_service = None
        model_error = str(exc)


@app.get("/health")
def health() -> Dict[str, Any]:
    if model_service is None:
        return {
            "model_loaded": False,
            "weights": str(DEFAULT_WEIGHTS),
            "device": "cuda:0" if torch.cuda.is_available() else "cpu",
            "cuda_available": torch.cuda.is_available(),
            "class_names": DEFAULT_NAMES,
            "error": model_error,
        }
    return model_service.health()


@app.post("/predict/image")
async def predict_image(
    file: UploadFile = File(...),
    conf: float = Query(0.25, ge=0.0, le=1.0),
    iou: float = Query(0.45, ge=0.0, le=1.0),
    imgsz: int = Query(640, ge=32, le=2048),
) -> Dict[str, Any]:
    service = get_model()
    if imgsz != service.imgsz[0]:
        service.imgsz = check_img_size((imgsz, imgsz), s=service.stride)

    contents = await file.read()
    image_array = np.frombuffer(contents, np.uint8)
    frame = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
    if frame is None:
        raise HTTPException(status_code=400, detail="Invalid image file. Please upload a readable image.")

    annotated, detections = service.predict_frame(frame, conf_thres=conf, iou_thres=iou)
    output_name = f"{Path(file.filename or 'image').stem}_{int(time.time())}_{uuid.uuid4().hex[:8]}.jpg"
    output_path = IMAGE_DIR / output_name
    cv2.imwrite(str(output_path), annotated)

    return {
        "success": True,
        "filename": file.filename,
        "image_shape": {"height": int(frame.shape[0]), "width": int(frame.shape[1])},
        "conf": conf,
        "detections": detections,
        "detection_count": len(detections),
        "output_image_path": str(output_path),
        "output_image_url": f"/runs/api/images/{output_name}",
    }


@app.post("/predict/video")
async def predict_video(
    file: UploadFile = File(...),
    conf: float = Query(0.25, ge=0.0, le=1.0),
    iou: float = Query(0.45, ge=0.0, le=1.0),
    imgsz: int = Query(640, ge=32, le=2048),
) -> Dict[str, Any]:
    service = get_model()
    if imgsz != service.imgsz[0]:
        service.imgsz = check_img_size((imgsz, imgsz), s=service.stride)

    suffix = Path(file.filename or "upload.mp4").suffix or ".mp4"
    stem = Path(file.filename or "upload").stem
    token = f"{int(time.time())}_{uuid.uuid4().hex[:8]}"
    input_path = UPLOAD_DIR / f"{stem}_{token}{suffix}"
    output_name = f"{stem}_{token}_detected.mp4"
    output_path = VIDEO_DIR / output_name

    with input_path.open("wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    cap = cv2.VideoCapture(str(input_path))
    if not cap.isOpened():
        raise HTTPException(status_code=400, detail="Invalid video file. OpenCV cannot read this upload.")

    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    frame_total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    writer = cv2.VideoWriter(str(output_path), cv2.VideoWriter_fourcc(*"mp4v"), fps, (width, height))
    if not writer.isOpened():
        cap.release()
        raise HTTPException(status_code=500, detail=f"Failed to create output video: {output_path}")

    frame_index = 0
    total_detections = 0
    per_class: Dict[str, int] = {name: 0 for name in service.names.values()}

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            annotated, detections = service.predict_frame(frame, conf_thres=conf, iou_thres=iou)
            writer.write(annotated)
            frame_index += 1
            total_detections += len(detections)
            for det in detections:
                per_class[det["class_name"]] = per_class.get(det["class_name"], 0) + 1
    finally:
        cap.release()
        writer.release()

    return {
        "success": True,
        "filename": file.filename,
        "conf": conf,
        "output_video_path": str(output_path),
        "output_video_url": f"/runs/api/videos/{output_name}",
        "stats": {
            "frames_reported_by_video": frame_total,
            "frames_processed": frame_index,
            "fps": fps,
            "width": width,
            "height": height,
            "total_detections": total_detections,
            "per_class": per_class,
        },
    }


def mjpeg_generator(source: str, conf: float, iou: float) -> Generator[bytes, None, None]:
    service = get_model()
    capture_source: Any = int(source) if source.isdigit() else source
    cap = cv2.VideoCapture(capture_source)
    if not cap.isOpened():
        raise HTTPException(status_code=400, detail=f"Cannot open video source: {source}")

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            annotated, _ = service.predict_frame(frame, conf_thres=conf, iou_thres=iou)
            ok, encoded = cv2.imencode(".jpg", annotated)
            if not ok:
                continue
            yield b"--frame\r\nContent-Type: image/jpeg\r\n\r\n" + encoded.tobytes() + b"\r\n"
    finally:
        cap.release()


@app.get("/predict/stream")
def predict_stream(
    source: str = Query("0", description="Camera index, video path, RTSP URL, or HTTP stream URL."),
    conf: float = Query(0.25, ge=0.0, le=1.0),
    iou: float = Query(0.45, ge=0.0, le=1.0),
) -> StreamingResponse:
    return StreamingResponse(
        mjpeg_generator(source=source, conf=conf, iou=iou),
        media_type="multipart/x-mixed-replace; boundary=frame",
    )
