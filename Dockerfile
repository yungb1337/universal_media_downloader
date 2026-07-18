# Use official lightweight Python image
FROM python:3.11-slim

# Install system dependencies (FFmpeg is required for yt-dlp to merge streams)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Set working directory inside container
WORKDIR /app

# Copy dependency definition
COPY requirements.txt .

# Install dependencies (plus FastAPI, Uvicorn, and python-multipart for the web server)
RUN pip install --no-cache-dir -r requirements.txt fastapi uvicorn python-multipart

# Copy the entire workspace into the container
COPY . .

# Create the downloads directory and grant permissions
RUN mkdir -p downloads && chmod 777 downloads

# Set environment variables
ENV PORT=7860
ENV DOWNLOAD_DIR=downloads

# Expose port (default for HF, Render overrides this)
EXPOSE 7860

# Run FastAPI app using Uvicorn on a dynamic port
CMD ["sh", "-c", "uvicorn web.app:app --host 0.0.0.0 --port ${PORT:-7860}"]
