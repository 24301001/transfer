#!/usr/bin/env python
"""
事故风险预测专家系统 - 启动入口
"""
import uvicorn
from src.config import HOST, PORT

if __name__ == "__main__":
    uvicorn.run(
        "src.main:app",
        host=HOST,
        port=PORT,
        reload=False,
        log_level="info",
    )
