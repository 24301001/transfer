# Traffic Dispatch RL

这个目录是给“交通事故智能识别与调度系统”准备的强化学习调度模块，不修改 Gymnasium 官方源码。

## 模块定位

已有算法链路：

1. YOLO 识别事故状态：碰撞、起火、翻车、损坏、车辆数量。
2. 风险模型输出人员风险、交通影响和综合风险等级。
3. 道路恢复时间模型输出预计恢复时间。
4. 本模块根据风险和事故状态推荐应急资源。

本模块分三部分：

- EMS Agent：基于 `export.csv` 急救历史调度数据，学习救护车调度强度。
- Fire Agent：基于 `export (1).csv` 消防历史调度数据，学习消防资源组合。
- Tow Rule：拖车/清障车缺少公开调度数据，先使用专家规则。

## 为什么不是直接用历史数据做标准强化学习

这两个 CSV 是历史调度记录，属于“专家已经采取过的动作和结果”，不是可交互环境。代码里采用工程上更容易落地的方式：

1. 从历史数据抽取事故状态和响应时间。
2. 构造 Gymnasium 单步仿真环境。
3. Agent 在仿真环境里选择资源动作。
4. reward 同时惩罚响应时间、资源成本、派遣不足和过度派遣。

答辩时建议表述为：`基于历史调度数据校准的应急资源调度强化学习仿真模块`。

## 使用步骤

在 `C:\Users\LENOVO\Desktop\Gymnasium-main\Gymnasium-main` 下运行。

### 1. 生成轻量训练表

原始 CSV 很大，默认只抽样 1%：

```bash
python -m traffic_dispatch_rl.preprocess --sample-frac 0.01
```

只处理急救：

```bash
python -m traffic_dispatch_rl.preprocess --only ems --sample-frac 0.01
```

只处理消防：

```bash
python -m traffic_dispatch_rl.preprocess --only fire --sample-frac 0.01
```

输出：

- `traffic_dispatch_rl/data/ems_processed.csv`
- `traffic_dispatch_rl/data/fire_processed.csv`

### 2. 训练 PPO Agent

如果已经安装 stable-baselines3：

```bash
python -m traffic_dispatch_rl.train --agent all --timesteps 20000
```

如果使用你下载的源码目录：

```bash
python -m traffic_dispatch_rl.train --agent all --timesteps 20000 --sb3-root "C:\Users\LENOVO\Desktop\stable-baselines3-master\stable-baselines3-master"
```

输出：

- `traffic_dispatch_rl/models/ems_ppo.zip`
- `traffic_dispatch_rl/models/fire_ppo.zip`

### 3. 调度推荐

示例：事故起火、高人员风险、翻车、5辆车、2条车道受影响。

```bash
python -m traffic_dispatch_rl.recommend --fire 1 --injury-risk high --car-flip 1 --vehicle-num 5 --affected-lanes 2 --lane-status 1
```

输出 JSON 可直接接入你的后端：

```json
{
  "ambulance": 2,
  "firetruck": 1,
  "towtruck": 2,
  "heavy_towtruck": 1,
  "details": []
}
```

## 后续怎么接你的项目

你的主系统只需要把 YOLO 和算法 2/3 的结果转换成参数：

- `fire`: YOLO 是否识别起火。
- `injury-risk`: 人员风险模型输出，`low` / `medium` / `high`。
- `car-flip`: YOLO 是否识别翻车。
- `vehicle-num`: YOLO 检测车辆数量。
- `affected-lanes`: 人工填报或道路影响模型输出。
- `lane-status`: 0 无影响，1 部分占用，2 完全封闭。

第一版先用命令行跑通，后面可以把 `recommend.py` 里的逻辑封装成 Django/FastAPI 接口。

