#!/bin/bash

# Build the Docker image
echo "🐳 Building algorithm Docker image..."
docker build -t my-logistic-algorithm:1.0.0 .

# Save as tar file for upload
echo "📦 Saving Docker image as tar file..."
docker save my-logistic-algorithm:1.0.0 -o my-logistic-algorithm.tar

echo "✅ Done! You can now upload:"
echo "   - Docker tar file: my-logistic-algorithm.tar"
echo "   - Parameters file: parameters.json"

echo ""
echo "📝 Algorithm info:"
echo "   - Name: MyLogisticAlgorithm"
echo "   - Version: 1.0.0"
echo "   - Description: Custom logistic regression using gradient descent"
echo "   - Keywords: logistic-regression, gradient-descent, binary-classification"