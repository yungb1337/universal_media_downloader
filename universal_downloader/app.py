import os
import uvicorn
from web.app import app

if __name__ == "__main__":
    # Hugging Face default port is 7860, but fallback to environment variable PORT if present
    port = int(os.environ.get("PORT", 7860))
    
    # Run the FastAPI server
    uvicorn.run("web.app:app", host="0.0.0.0", port=port)
