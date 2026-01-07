import numpy as np
import pickle

class Algorithm:
    def __init__(self, params: dict):
        """Initialize the algorithm with parameters from params.json"""
        # Example hyperparams from params.json
        self.learning_rate = params.get("learning_rate", 0.01)
        self.n_epochs = params.get("n_epochs", 1000)
        self.weights = None
        print(f"🔧 Algorithm initialized with learning_rate={self.learning_rate}, n_epochs={self.n_epochs}")

    def fit(self, X, y):
        """Train a custom logistic regression from scratch using gradient descent."""
        print("🚀 Starting training...")

        X = np.array(X)
        y = np.array(y)
        print(f"📊 Training data shape: X={X.shape}, y={y.shape}")

        # Add bias column (intercept)
        X = np.c_[np.ones(X.shape[0]), X]
        n_samples, n_features = X.shape
        print(f"📈 Features (with bias): {n_features}")

        # Initialize weights randomly
        np.random.seed(42)  # For reproducibility
        self.weights = np.random.normal(0, 0.01, n_features)
        print(f"⚖️ Initial weights: {self.weights}")

        # Gradient descent training loop
        for epoch in range(self.n_epochs):
            # Forward pass
            linear = np.dot(X, self.weights)
            y_pred = 1 / (1 + np.exp(-np.clip(linear, -500, 500)))  # Clip to prevent overflow

            # Compute loss (cross-entropy)
            loss = -np.mean(y * np.log(y_pred + 1e-15) + (1 - y) * np.log(1 - y_pred + 1e-15))

            # Backward pass (gradient computation)
            gradient = np.dot(X.T, (y_pred - y)) / n_samples

            # Update weights
            self.weights -= self.learning_rate * gradient

            # Print progress every 100 epochs
            if epoch % 100 == 0:
                accuracy = np.mean((y_pred > 0.5) == y)
                print(f"📊 Epoch {epoch}/{self.n_epochs}: Loss={loss:.4f}, Accuracy={accuracy:.4f}")

        final_loss = -np.mean(y * np.log(y_pred + 1e-15) + (1 - y) * np.log(1 - y_pred + 1e-15))
        final_accuracy = np.mean((y_pred > 0.5) == y)
        print(f"✅ Training completed! Final Loss={final_loss:.4f}, Final Accuracy={final_accuracy:.4f}")
        print(f"🎯 Final weights: {self.weights}")

    def predict(self, X):
        """Run prediction using trained weights."""
        if self.weights is None:
            raise ValueError("❌ Model not trained yet. Call fit() first.")

        print("🔮 Making predictions...")
        X = np.array(X)
        print(f"📊 Prediction data shape: {X.shape}")

        # Add bias column (same as in training)
        X = np.c_[np.ones(X.shape[0]), X]

        # Forward pass
        linear = np.dot(X, self.weights)
        y_pred_prob = 1 / (1 + np.exp(-np.clip(linear, -500, 500)))
        y_pred = (y_pred_prob >= 0.5).astype(int)

        print(f"🎯 Generated {len(y_pred)} predictions")
        print(f"📈 Prediction distribution: {np.bincount(y_pred)}")

        return y_pred