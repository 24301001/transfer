import os
import time
import warnings
import joblib
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    mean_squared_error,
    mean_absolute_error,
    r2_score,
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    classification_report
)
from sklearn.ensemble import (
    RandomForestRegressor,
    ExtraTreesRegressor,
    GradientBoostingRegressor,
    RandomForestClassifier,
    ExtraTreesClassifier,
    GradientBoostingClassifier
)
from sklearn.tree import DecisionTreeRegressor, DecisionTreeClassifier
from sklearn.linear_model import LinearRegression, LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline

from xgboost import XGBRegressor, XGBClassifier
from lightgbm import LGBMRegressor, LGBMClassifier

warnings.filterwarnings("ignore")

DATA_PATH = "accident_recovery_dataset.csv"
OUT_DIR = "recovery_model_comparison_figures"
os.makedirs(OUT_DIR, exist_ok=True)

sns.set(style="whitegrid", font_scale=1.1)

df = pd.read_csv(DATA_PATH)

# 沿用论文常见思路：去掉极端duration
q01 = df["duration"].quantile(0.01)
q99 = df["duration"].quantile(0.99)
df = df[(df["duration"] >= q01) & (df["duration"] <= q99)]
df = df[df["duration"] > 0]

X = df.drop(columns=["duration"])
y_reg = df["duration"]

# 分类标签：超过30分钟记为长时事故
THRESHOLD = 30
y_cls = (df["duration"] > THRESHOLD).astype(int)

X_train_reg, X_test_reg, y_train_reg, y_test_reg = train_test_split(
    X, y_reg, test_size=0.2, random_state=42
)

X_train_cls, X_test_cls, y_train_cls, y_test_cls = train_test_split(
    X, y_cls, test_size=0.2, random_state=42, stratify=y_cls
)

# =========================
# 回归模型
# =========================

reg_models = {
    "LinearRegression": Pipeline([
        ("scaler", StandardScaler()),
        ("model", LinearRegression())
    ]),
    "DecisionTree": DecisionTreeRegressor(random_state=42, max_depth=12),
    "RandomForest": RandomForestRegressor(
        n_estimators=200,
        random_state=42,
        n_jobs=-1,
        max_depth=16
    ),
    "ExtraTrees": ExtraTreesRegressor(
        n_estimators=200,
        random_state=42,
        n_jobs=-1,
        max_depth=16
    ),
    "GradientBoosting": GradientBoostingRegressor(random_state=42),
    "XGBoost": XGBRegressor(
        objective="reg:squarederror",
        n_estimators=300,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        n_jobs=-1
    ),
    "LightGBM": LGBMRegressor(
        n_estimators=300,
        max_depth=-1,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        n_jobs=-1,
        verbose=-1
    )
}

reg_results = []
best_reg_model = None
best_reg_rmse = float("inf")
best_reg_name = None

for name, model in reg_models.items():
    print(f"Training regression model: {name}")
    start = time.time()
    model.fit(X_train_reg, y_train_reg)
    train_time = time.time() - start

    start = time.time()
    pred = model.predict(X_test_reg)
    infer_time = time.time() - start

    rmse = np.sqrt(mean_squared_error(y_test_reg, pred))
    mae = mean_absolute_error(y_test_reg, pred)
    r2 = r2_score(y_test_reg, pred)

    reg_results.append({
        "model": name,
        "rmse": rmse,
        "mae": mae,
        "r2": r2,
        "train_time_sec": train_time,
        "inference_time_sec": infer_time
    })

    if rmse < best_reg_rmse:
        best_reg_rmse = rmse
        best_reg_model = model
        best_reg_name = name

reg_df = pd.DataFrame(reg_results).sort_values("rmse")
reg_df.to_csv(f"{OUT_DIR}/regression_model_metrics.csv", index=False)

plt.figure(figsize=(9, 5))
sns.barplot(data=reg_df, x="model", y="rmse")
plt.xticks(rotation=35, ha="right")
plt.ylabel("RMSE")
plt.xlabel("Regression Model")
plt.title("Regression Model Comparison - RMSE")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/01_regression_rmse_comparison.png", dpi=300)
plt.close()

plt.figure(figsize=(9, 5))
sns.barplot(data=reg_df, x="model", y="mae")
plt.xticks(rotation=35, ha="right")
plt.ylabel("MAE")
plt.xlabel("Regression Model")
plt.title("Regression Model Comparison - MAE")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/02_regression_mae_comparison.png", dpi=300)
plt.close()

plt.figure(figsize=(9, 5))
sns.barplot(data=reg_df, x="model", y="train_time_sec")
plt.xticks(rotation=35, ha="right")
plt.ylabel("Training Time (s)")
plt.xlabel("Regression Model")
plt.title("Regression Model Training Time")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/03_regression_training_time.png", dpi=300)
plt.close()

# =========================
# 回归特征重要性
# =========================

def get_feature_importance(model, feature_names):
    if hasattr(model, "feature_importances_"):
        return pd.DataFrame({
            "feature": feature_names,
            "importance": model.feature_importances_
        }).sort_values("importance", ascending=False)
    if hasattr(model, "named_steps"):
        inner = model.named_steps.get("model")
        if hasattr(inner, "coef_"):
            return pd.DataFrame({
                "feature": feature_names,
                "importance": np.abs(inner.coef_)
            }).sort_values("importance", ascending=False)
    return None

importance_df = get_feature_importance(best_reg_model, X.columns)

if importance_df is not None:
    importance_df.to_csv(f"{OUT_DIR}/best_regression_feature_importance.csv", index=False)

    plt.figure(figsize=(9, 7))
    sns.barplot(data=importance_df.head(15), x="importance", y="feature")
    plt.xlabel("Importance")
    plt.ylabel("Feature")
    plt.title(f"Top 15 Feature Importance - {best_reg_name}")
    plt.tight_layout()
    plt.savefig(f"{OUT_DIR}/04_best_regression_feature_importance.png", dpi=300)
    plt.close()

# =========================
# 分类模型
# =========================

cls_models = {
    "LogisticRegression": Pipeline([
        ("scaler", StandardScaler()),
        ("model", LogisticRegression(max_iter=1000))
    ]),
    "DecisionTree": DecisionTreeClassifier(random_state=42, max_depth=12),
    "RandomForest": RandomForestClassifier(
        n_estimators=200,
        random_state=42,
        n_jobs=-1,
        max_depth=16
    ),
    "ExtraTrees": ExtraTreesClassifier(
        n_estimators=200,
        random_state=42,
        n_jobs=-1,
        max_depth=16
    ),
    "GradientBoosting": GradientBoostingClassifier(random_state=42),
    "XGBoost": XGBClassifier(
        n_estimators=300,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        n_jobs=-1,
        eval_metric="logloss"
    ),
    "LightGBM": LGBMClassifier(
        n_estimators=300,
        max_depth=-1,
        learning_rate=0.05,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        n_jobs=-1,
        verbose=-1
    )
}

cls_results = []
best_cls_model = None
best_cls_f1 = -1
best_cls_name = None

for name, model in cls_models.items():
    print(f"Training classification model: {name}")
    start = time.time()
    model.fit(X_train_cls, y_train_cls)
    train_time = time.time() - start

    start = time.time()
    pred = model.predict(X_test_cls)
    infer_time = time.time() - start

    acc = accuracy_score(y_test_cls, pred)
    precision = precision_score(y_test_cls, pred, zero_division=0)
    recall = recall_score(y_test_cls, pred, zero_division=0)
    f1 = f1_score(y_test_cls, pred, zero_division=0)

    cls_results.append({
        "model": name,
        "accuracy": acc,
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "train_time_sec": train_time,
        "inference_time_sec": infer_time
    })

    with open(f"{OUT_DIR}/classification_report_{name}.txt", "w", encoding="utf-8") as f:
        f.write(classification_report(y_test_cls, pred, zero_division=0))

    if f1 > best_cls_f1:
        best_cls_f1 = f1
        best_cls_model = model
        best_cls_name = name

cls_df = pd.DataFrame(cls_results).sort_values("f1", ascending=False)
cls_df.to_csv(f"{OUT_DIR}/classification_model_metrics.csv", index=False)

plt.figure(figsize=(9, 5))
sns.barplot(data=cls_df, x="model", y="f1")
plt.xticks(rotation=35, ha="right")
plt.ylabel("F1 Score")
plt.xlabel("Classification Model")
plt.title(f"Classification Model Comparison - F1 (threshold={THRESHOLD}min)")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/05_classification_f1_comparison.png", dpi=300)
plt.close()

plt.figure(figsize=(9, 5))
sns.barplot(data=cls_df, x="model", y="accuracy")
plt.xticks(rotation=35, ha="right")
plt.ylabel("Accuracy")
plt.xlabel("Classification Model")
plt.title(f"Classification Model Comparison - Accuracy (threshold={THRESHOLD}min)")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/06_classification_accuracy_comparison.png", dpi=300)
plt.close()

plt.figure(figsize=(9, 5))
sns.barplot(data=cls_df, x="model", y="train_time_sec")
plt.xticks(rotation=35, ha="right")
plt.ylabel("Training Time (s)")
plt.xlabel("Classification Model")
plt.title("Classification Model Training Time")
plt.tight_layout()
plt.savefig(f"{OUT_DIR}/07_classification_training_time.png", dpi=300)
plt.close()

# =========================
# 分类特征重要性
# =========================

cls_importance_df = get_feature_importance(best_cls_model, X.columns)

if cls_importance_df is not None:
    cls_importance_df.to_csv(f"{OUT_DIR}/best_classification_feature_importance.csv", index=False)

    plt.figure(figsize=(9, 7))
    sns.barplot(data=cls_importance_df.head(15), x="importance", y="feature")
    plt.xlabel("Importance")
    plt.ylabel("Feature")
    plt.title(f"Top 15 Feature Importance - {best_cls_name}")
    plt.tight_layout()
    plt.savefig(f"{OUT_DIR}/08_best_classification_feature_importance.png", dpi=300)
    plt.close()

# 保存最佳模型
joblib.dump(
    {
        "model": best_reg_model,
        "features": list(X.columns),
        "task": "regression",
        "best_model_name": best_reg_name,
        "metrics": reg_df.to_dict(orient="records")
    },
    "best_recovery_regression_model.pkl"
)

joblib.dump(
    {
        "model": best_cls_model,
        "features": list(X.columns),
        "task": "classification",
        "threshold_minutes": THRESHOLD,
        "best_model_name": best_cls_name,
        "metrics": cls_df.to_dict(orient="records")
    },
    "best_recovery_classification_model.pkl"
)

# 总结文件
with open(f"{OUT_DIR}/summary.txt", "w", encoding="utf-8") as f:
    f.write("Recovery Time Model Comparison Summary\n")
    f.write("=" * 50 + "\n\n")
    f.write(f"Rows used: {len(df)}\n")
    f.write(f"Classification threshold: {THRESHOLD} minutes\n\n")
    f.write("Best regression model:\n")
    f.write(f"{best_reg_name}, RMSE={best_reg_rmse:.4f}\n\n")
    f.write("Regression metrics:\n")
    f.write(reg_df.to_string(index=False))
    f.write("\n\nBest classification model:\n")
    f.write(f"{best_cls_name}, F1={best_cls_f1:.4f}\n\n")
    f.write("Classification metrics:\n")
    f.write(cls_df.to_string(index=False))

print("All comparison figures generated in:", OUT_DIR)
print("Best regression model:", best_reg_name, "RMSE:", best_reg_rmse)
print("Best classification model:", best_cls_name, "F1:", best_cls_f1)
print("Saved:")
print(" - best_recovery_regression_model.pkl")
print(" - best_recovery_classification_model.pkl")
