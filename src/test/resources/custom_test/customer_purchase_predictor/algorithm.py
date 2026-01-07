import numpy as np
import pickle

class Algorithm:
    def __init__(self, params: dict):
        """Initialize the algorithm with parameters from params.json"""
        self.learning_rate = float(params.get("learning_rate", 0.01))
        self.n_epochs = int(params.get("n_epochs", 500))
        self.weights = None
        print(f"🔧 Customer Purchase Predictor initialized")
        print(f"   Learning Rate: {self.learning_rate}")
        print(f"   Epochs: {self.n_epochs}")

    def fit(self, X, y):
        """Train logistic regression model for purchase prediction"""
        print("\n🚀 Starting training...")

        X = np.array(X, dtype=float)
        y = np.array(y, dtype=float)

        print(f"📊 Training data: {X.shape[0]} samples, {X.shape[1]} features")
        print(f"📈 Target distribution: {np.bincount(y.astype(int))}")

        # Normalize features for better training
        self.mean = np.mean(X, axis=0)
        self.std = np.std(X, axis=0) + 1e-8
        X_norm = (X - self.mean) / self.std

        # Add bias term
        X_bias = np.c_[np.ones(X.shape[0]), X_norm]
        n_samples, n_features = X_bias.shape

        # Initialize weights
        np.random.seed(42)
        self.weights = np.random.randn(n_features) * 0.01

        # Training loop
        for epoch in range(self.n_epochs):
            # Forward pass
            linear = np.dot(X_bias, self.weights)
            predictions = 1 / (1 + np.exp(-np.clip(linear, -500, 500)))

            # Compute loss
            loss = -np.mean(y * np.log(predictions + 1e-15) +
                          (1 - y) * np.log(1 - predictions + 1e-15))

            # Gradient descent
            gradient = np.dot(X_bias.T, (predictions - y)) / n_samples
            self.weights -= self.learning_rate * gradient

            # Log progress
            if epoch % 100 == 0 or epoch == self.n_epochs - 1:
                accuracy = np.mean((predictions > 0.5) == y)
                print(f"   Epoch {epoch:4d}: Loss={loss:.4f}, Accuracy={accuracy:.4f}")

        final_accuracy = np.mean((predictions > 0.5) == y)
        print(f"\n✅ Training complete! Final accuracy: {final_accuracy:.4f}")
        print(f"🎯 Model weights: {self.weights}")

    def predict(self, X):
        """Make purchase predictions"""
        if self.weights is None:
            raise ValueError("❌ Model not trained! Call fit() first.")

        print("\n🔮 Making predictions...")

        X = np.array(X, dtype=float)
        print(f"📊 Prediction data: {X.shape[0]} samples")

        # Normalize using training statistics
        X_norm = (X - self.mean) / self.std

        # Add bias term
        X_bias = np.c_[np.ones(X.shape[0]), X_norm]

        # Predict
        linear = np.dot(X_bias, self.weights)
        probabilities = 1 / (1 + np.exp(-np.clip(linear, -500, 500)))
        predictions = (probabilities >= 0.5).astype(int)

        print(f"🎯 Predictions: {predictions}")
        print(f"📈 Distribution: {np.bincount(predictions)} (0=No Purchase, 1=Purchase)")

        return predictions
