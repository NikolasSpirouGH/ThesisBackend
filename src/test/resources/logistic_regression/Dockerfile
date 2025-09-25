# Use Python 3.11 slim image as base
FROM python:3.11-slim

# Set working directory
WORKDIR /app

# Install system dependencies if needed
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better Docker layer caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the algorithm file
COPY algorithm.py .

# Create a non-root user for security
RUN useradd -m -u 1000 algorithmuser && chown -R algorithmuser:algorithmuser /app
USER algorithmuser

# The entry point will be provided by the training system
# No CMD needed - the system will run train.py or predict.py automatically