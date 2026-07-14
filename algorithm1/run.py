#!/usr/bin/env python
"""
Algorithm1 - YOLOv5 事故图像检测 API 启动入口
"""
import os
import uvicorn

if __name__ == "__main__":
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("inference_api:app", host=host, port=port, reload=False, log_level="info")
