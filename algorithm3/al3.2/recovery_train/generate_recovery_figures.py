import os
import joblib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score
from xgboost import XGBRegressor

DATA_PATH = "accident_recovery_dataset.csv"
OUT_DIR = "recovery_figures"

os.makedirs(OUT_DIR, exist_ok=True)

sns.set(style="whitegrid", font_scale=1.1)

df = pd.read_csv(DATA_PATH)

q01 = df["duration"].quantile(0.01)
q99 = df["duration"].quantile(0.99)
df = df[(df["duration"] >= q01) & (df["duration"] <= q99)]
df = df[df["duration"] > 0]

# 1. Duration distribution
plt.figure(figsize=(8, 5))
sns.histplot(df["duration"], bins=60, kde=True)
plt.xlabel("Duration (minutes)")
plt.ylabel("Count")
plt.title("Distribution of Incident Recovery Duration")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/01_duration_distribution.png", dpi=300)
plt.close()

# 2. Boxplots for important categorical/binary features
plot_features = [
    "car_crash",
    "car_damage",
    "fire",
    "car_flip",
    "risk_level",
    "lane_status",
    "traffic_density",
    "road_type"
]

for i, col in enumerate(plot_features, start=2):
    if col in df.columns:
        plt.figure(figsize=(7, 5))
        sns.boxplot(x=col, y="duration", data=df)
        plt.xlabel(col)
        plt.ylabel("Duration (minutes)")
        plt.title(f"Duration by {col}")
        plt.tight_layout()
        plt.savefig(f"{OUT_DIR}/{i:02d}_duration_by_{col}.png", dpi=300)
        plt.close()

# 3. Correlation heatmap
plt.figure(figsize=(14, 10))
corr = df.corr(numeric_only=True)
sns.heatmap(corr, cmap="coolwarm", center=0, linewidths=0.3)
plt.title("Feature Correlation Heatmap")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/10_feature_correlation_heatmap.png", dpi=300)
plt.close()

# 4. Train XGBoost again for feature importance and prediction figures
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

# 5. Feature importance
importance = pd.DataFrame({
    "feature": X.columns,
    "importance": model.feature_importances_
}).sort_values("importance", ascending=False)

plt.figure(figsize=(9, 7))
sns.barplot(
    data=importance.head(15),
    x="importance",
    y="feature"
)
plt.xlabel("Importance")
plt.ylabel("Feature")
plt.title("Top 15 Feature Importance - XGBoost")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/11_xgboost_feature_importance.png", dpi=300)
plt.close()

importance.to_csv(f"{OUT_DIR}/xgboost_feature_importance.csv", index=False)

# 6. Prediction vs actual
plt.figure(figsize=(7, 7))
plt.scatter(y_test, y_pred, alpha=0.25, s=10)
min_val = min(y_test.min(), y_pred.min())
max_val = max(y_test.max(), y_pred.max())
plt.plot([min_val, max_val], [min_val, max_val], "r--")
plt.xlabel("Actual Duration (minutes)")
plt.ylabel("Predicted Duration (minutes)")
plt.title("Predicted vs Actual Recovery Duration")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/12_predicted_vs_actual.png", dpi=300)
plt.close()

# 7. Residual distribution
residuals = y_test - y_pred
plt.figure(figsize=(8, 5))
sns.histplot(residuals, bins=60, kde=True)
plt.xlabel("Residual (Actual - Predicted)")
plt.ylabel("Count")
plt.title("Residual Distribution")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/13_residual_distribution.png", dpi=300)
plt.close()

# 8. Metrics summary
with open(f"{OUT_DIR}/model_metrics.txt", "w", encoding="utf-8") as f:
    f.write("XGBoost Recovery Time Prediction Metrics\n")
    f.write(f"Rows used: {len(df)}\n")
    f.write(f"RMSE: {rmse:.4f}\n")
    f.write(f"MAE: {mae:.4f}\n")
    f.write(f"R2: {r2:.4f}\n")

print("Figures generated in:", OUT_DIR)
print(f"RMSE: {rmse:.4f}")
print(f"MAE: {mae:.4f}")
print(f"R2: {r2:.4f}")
