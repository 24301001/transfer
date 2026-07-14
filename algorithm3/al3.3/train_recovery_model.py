import pandas as pd
import numpy as np
import joblib

from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score
from xgboost import XGBRegressor

DATA_PATH = "accident_recovery_dataset.csv"
MODEL_PATH = "recovery_time_xgboost.pkl"

df = pd.read_csv(DATA_PATH)

q01 = df["duration"].quantile(0.01)
q99 = df["duration"].quantile(0.99)
df = df[(df["duration"] >= q01) & (df["duration"] <= q99)]
df = df[df["duration"] > 0]

X = df.drop(columns=["duration"])
y = df["duration"]

X_train, X_test, y_train, y_test = train_test_split(
    X, y,
    test_size=0.2,
    random_state=42
)

model = XGBRegressor(
    objective="reg:squarederror",
    n_estimators=300,
    max_depth=6,
    learning_rate=0.05,
    subsample=0.8,
    colsample_bytree=0.8,
    random_state=42,
    n_jobs=-1
)

model.fit(X_train, y_train)

y_pred = model.predict(X_test)

rmse = np.sqrt(mean_squared_error(y_test, y_pred))
mae = mean_absolute_error(y_test, y_pred)
r2 = r2_score(y_test, y_pred)

print("Training finished.")
print(f"Rows used: {len(df)}")
print(f"RMSE: {rmse:.4f}")
print(f"MAE: {mae:.4f}")
print(f"R2: {r2:.4f}")

joblib.dump(
    {
        "model": model,
        "features": list(X.columns),
        "metrics": {
            "rmse": rmse,
            "mae": mae,
            "r2": r2
        }
    },
    MODEL_PATH
)

print(f"Model saved to: {MODEL_PATH}")
