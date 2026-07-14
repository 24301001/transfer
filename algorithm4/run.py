import os
import uvicorn

if __name__ == "__main__":
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8004"))
    uvicorn.run("dispatch_api:app", host=host, port=port, reload=False)
