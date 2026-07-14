<template>
  <div class="command-center-page">
    <!-- 全屏地图背景 -->
    <div class="map-stage">
      <MapCard
        ref="mapRef"
        class="command-map"
        height="100%"
        width="100%"
        title="交通事故态势地图"
        :markers="mapMarkers"
        :show-hint="false"
        :show-zoom-controls="false"
        :show-native-controls="false"
        :show-marker-labels="false"
        fit-view-once
      />
    </div>
    <div class="map-vignette"></div>
    <div class="map-grid-overlay"></div>

    <!-- 顶部标题区 -->
    <header class="command-header">
      <div class="header-corner header-time">
        <div class="time-value">{{ currentTime }}</div>
        <div class="date-value">{{ currentDate }} · {{ currentWeekday }}</div>
      </div>

      <div class="center-title">
        <span class="title-line"></span>
        <div class="title-copy">
          <h1>交通事故风险预估与指挥中心</h1>
          <p>TRAFFIC ACCIDENT RISK FORECAST &amp; COMMAND CENTER</p>
        </div>
        <span class="title-line is-right"></span>
      </div>

      <div class="header-corner header-user">
        <el-dropdown trigger="click" popper-class="command-user-popper" @command="handleUserCommand">
          <button class="user-console" type="button">
            <el-avatar :size="30" :icon="UserFilled" />
            <span class="user-name">{{ userStore.nickname || '指挥人员' }}</span>
            <span class="user-role">{{ userStore.roleLabel }}</span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 左侧图表悬浮栏 -->
    <aside class="side-rail left-rail" :class="{ 'is-collapsed': leftCollapsed }">
      <section class="hud-panel chart-panel">
        <div class="panel-heading">
          <div>
            <h2>事故场景分布</h2>
            <p>ACCIDENT SCENE DISTRIBUTION</p>
          </div>
          <span class="panel-total">全部数据</span>
        </div>
        <div class="chart-body">
          <CommandSemiGauge
            :data="sceneChartData"
            :selected-name="activeChartFilter.dimension === 'scene' ? activeChartFilter.value : ''"
            total-label="场景总量"
            @select="(name) => handleChartSelect('scene', name)"
          />
        </div>
      </section>

      <section class="hud-panel chart-panel">
        <div class="panel-heading">
          <div>
            <h2>处理阶段分布</h2>
            <p>PROCESSING STAGE DISTRIBUTION</p>
          </div>
          <span class="panel-total">全部数据</span>
        </div>
        <div class="chart-body">
          <CommandSemiGauge
            :data="statusChartData"
            :selected-name="activeChartFilter.dimension === 'status' ? activeChartFilter.value : ''"
            total-label="流程总量"
            @select="(name) => handleChartSelect('status', name)"
          />
        </div>
      </section>
    </aside>

    <button
      class="rail-toggle left-toggle"
      :class="{ 'is-collapsed': leftCollapsed }"
      type="button"
      :title="leftCollapsed ? '展开数据图表' : '收起数据图表'"
      @click="leftCollapsed = !leftCollapsed"
    >
      <el-icon><DArrowRight v-if="leftCollapsed" /><DArrowLeft v-else /></el-icon>
    </button>

    <!-- 右侧查询与调度历史悬浮栏 -->
    <aside class="side-rail right-rail" :class="{ 'is-collapsed': rightCollapsed }">
      <section class="hud-panel incident-panel">
        <div class="panel-heading compact-heading">
          <div>
            <h2>事故查询</h2>
            <p>INCIDENT SEARCH</p>
          </div>
          <div class="heading-actions">
            <span v-if="queryActiveCount" class="filter-count">{{ queryActiveCount }} 项筛选</span>
            <button class="icon-action" type="button" title="刷新数据" @click="refreshAll">
              <el-icon :class="{ 'is-loading': refreshing }"><Refresh /></el-icon>
            </button>
          </div>
        </div>

        <div class="query-controls">
          <div class="quick-search-row">
            <el-input
              v-model="draftFilters.keyword"
              size="small"
              clearable
              placeholder="搜索编号、地点、描述…"
              @keyup.enter="applyIncidentFilters"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button class="query-button" type="primary" size="small" @click="applyIncidentFilters">
              查询
            </el-button>
            <button class="advanced-button" type="button" @click="advancedVisible = !advancedVisible">
              <el-icon><Filter /></el-icon>
              <span>高级</span>
              <el-icon class="advanced-arrow" :class="{ 'is-open': advancedVisible }"><ArrowDown /></el-icon>
            </button>
          </div>

          <el-collapse-transition>
            <div v-show="advancedVisible" class="advanced-filters">
              <el-input v-model="draftFilters.caseNo" size="small" clearable placeholder="事故编号" />
              <el-select v-model="draftFilters.status" size="small" clearable placeholder="处理状态">
                <el-option label="待处理" value="待处理" />
                <el-option label="处理中" value="处理中" />
                <el-option label="已处理" value="已处理" />
                <el-option label="已结案" value="已结案" />
              </el-select>
              <el-select v-model="draftFilters.riskLevel" size="small" clearable placeholder="风险等级">
                <el-option label="低" value="低" />
                <el-option label="中" value="中" />
                <el-option label="高" value="高" />
                <el-option label="严重" value="严重" />
              </el-select>
              <el-select v-model="draftFilters.type" size="small" clearable filterable placeholder="事故类型">
                <el-option v-for="type in accidentTypeOptions" :key="type" :label="type" :value="type" />
              </el-select>
              <el-date-picker
                v-model="draftFilters.dateRange"
                class="date-range-filter"
                type="daterange"
                size="small"
                unlink-panels
                clearable
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                range-separator="至"
                popper-class="command-date-popper"
              />
              <div class="advanced-actions">
                <button type="button" class="text-action" @click="resetIncidentFilters">重置条件</button>
                <button type="button" class="text-action is-primary" @click="applyIncidentFilters">应用筛选</button>
              </div>
            </div>
          </el-collapse-transition>

          <div class="result-summary">
            <span>
              匹配 <strong>{{ filteredAccidents.length }}</strong> / {{ allAccidents.length }} 起事故
            </span>
            <span v-if="activeChartFilter.value" class="linked-filter">
              图表联动：{{ activeChartFilter.value }}
            </span>
          </div>
        </div>

        <div class="incident-list hud-scroll" v-loading="accidentStore.loading">
          <article
            v-for="item in filteredAccidents"
            :key="item.id"
            class="incident-row"
            :class="{ 'is-active': selectedAccidentId === item.id && drawerMode === 'accident' }"
            @click="openAccident(item)"
          >
            <div class="row-main">
              <div class="row-topline">
                <span class="case-number">{{ item.caseNo || `#${item.id}` }}</span>
                <RiskBadge :level="item.riskLevel" size="small" />
              </div>
              <div class="row-title">{{ item.type || '未识别事故' }}</div>
              <div class="row-location">
                <el-icon><Location /></el-icon>
                <span>{{ item.location?.name || '地点信息待补充' }}</span>
              </div>
            </div>
            <div class="row-meta">
              <span class="status-chip" :class="statusClass(item.status)">{{ item.status || '-' }}</span>
              <time>{{ compactDateTime(item.reportTime) }}</time>
              <button
                class="media-view-btn"
                type="button"
                :title="`查看 ${item.caseNo || '#' + item.id} YOLO 分析媒体`"
                @click.stop="openMediaDialog(item)"
              >
                <el-icon><VideoCamera /></el-icon>
              </button>
            </div>
          </article>
          <div v-if="!filteredAccidents.length && !accidentStore.loading" class="empty-state">
            <el-icon><Search /></el-icon>
            <span>暂无符合条件的事故记录</span>
          </div>
        </div>
      </section>

      <section class="hud-panel dispatch-panel">
        <div class="panel-heading compact-heading">
          <div>
            <h2>调度任务历史</h2>
            <p>DISPATCH TASK HISTORY</p>
          </div>
          <span class="panel-total">{{ filteredTasks.length }} 条</span>
        </div>

        <div class="dispatch-list hud-scroll" v-loading="taskLoading">
          <article
            v-for="task in filteredTasks"
            :key="task.id"
            class="dispatch-row"
            :class="{ 'is-active': selectedTask?.id === task.id && drawerMode === 'task' }"
            @click="openTask(task)"
          >
            <div class="dispatch-icon"><el-icon><Van /></el-icon></div>
            <div class="dispatch-copy">
              <div class="dispatch-topline">
                <span>{{ task.taskNo || `任务 #${task.id}` }}</span>
                <span class="task-status" :class="taskStatusClass(task.status)">{{ task.status }}</span>
              </div>
              <div class="dispatch-title">
                {{ task.accidentType || relatedAccident(task)?.type || '关联事故' }}
                <span>· {{ task.vehicleType || '-' }}</span>
              </div>
              <div class="dispatch-meta">
                <span>{{ task.assignedTo || '待分配人员' }}</span>
                <time>{{ compactDateTime(task.createTime) }}</time>
              </div>
            </div>
            <el-icon class="locate-arrow"><Aim /></el-icon>
          </article>
          <div v-if="!filteredTasks.length && !taskLoading" class="empty-state compact-empty">
            <el-icon><Van /></el-icon>
            <span>暂无调度历史</span>
          </div>
        </div>
      </section>
    </aside>

    <button
      class="rail-toggle right-toggle"
      :class="{ 'is-collapsed': rightCollapsed }"
      type="button"
      :title="rightCollapsed ? '展开查询面板' : '收起查询面板'"
      @click="rightCollapsed = !rightCollapsed"
    >
      <el-icon><DArrowLeft v-if="rightCollapsed" /><DArrowRight v-else /></el-icon>
    </button>

    <!-- 地图底部运行状态 -->
    <div class="map-status-strip">
      <span><i class="online-dot"></i>实时数据同步</span>
      <span>地图事故 <strong>{{ filteredAccidents.length }}</strong></span>
      <span>调度任务 <strong>{{ filteredTasks.length }}</strong></span>
      <span>更新于 {{ lastUpdatedTime || '--:--:--' }}</span>
    </div>

    <!-- 事故 / 调度统一详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      class="command-detail-drawer"
      :title="drawerMode === 'task' ? '调度任务详情' : '事故详情'"
      :size="460"
      direction="rtl"
      modal-class="command-drawer-modal"
    >
      <template v-if="drawerMode === 'task' && selectedTask">
        <div class="drawer-hero">
          <div>
            <span class="drawer-eyebrow">DISPATCH TASK</span>
            <h3>{{ selectedTask.taskNo || `调度任务 #${selectedTask.id}` }}</h3>
          </div>
          <span class="drawer-status" :class="taskStatusClass(selectedTask.status)">{{ selectedTask.status }}</span>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="事故编号">{{ selectedAccident.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="事故类型">{{ selectedAccident.type }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <RiskBadge :level="selectedAccident.riskLevel" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ selectedAccident.riskScore }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(selectedAccident.status)" size="small" effect="plain">
              {{ selectedAccident.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="地点">{{ selectedAccident.location?.name }}</el-descriptions-item>
          <el-descriptions-item label="上报时间">{{ selectedAccident.reportTime }}</el-descriptions-item>
          <el-descriptions-item label="场景识别" v-if="drawerDetail?.sceneLabels?.length">
            <div class="drawer-tags">
              <el-tag
                v-for="label in drawerDetail.sceneLabels"
                :key="label"
                size="small"
                effect="plain"
              >
                {{ label }}
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="是否有人受伤">
            <el-tag :type="(drawerDetail || selectedAccident).injuryReported ? 'danger' : 'success'" size="small">
              {{ (drawerDetail || selectedAccident).injuryReported ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="受伤人数">{{ (drawerDetail || selectedAccident).injuredCount || 0 }}人</el-descriptions-item>
          <el-descriptions-item label="预计拥堵">{{ selectedAccident.congestionDuration }}</el-descriptions-item>
          <el-descriptions-item label="恢复时间">{{ selectedAccident.recoveryTime }}</el-descriptions-item>
          <el-descriptions-item label="影响车道">{{ selectedAccident.affectedLanes }}</el-descriptions-item>
          <el-descriptions-item label="天气">{{ selectedAccident.weather }}</el-descriptions-item>
          <el-descriptions-item label="道路等级">{{ selectedAccident.roadLevel }}</el-descriptions-item>
          <el-descriptions-item label="处置建议">
            <el-alert
              v-if="selectedAccident.disposalAdvice"
              :title="selectedAccident.disposalAdvice"
              type="info"
              :closable="false"
              show-icon
            />
            <span v-else class="text-muted">暂无建议</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-algorithm-panel">
          <div class="drawer-algorithm-head">
            <h4>算法2风险评估</h4>
            <el-tag v-if="(drawerDetail || selectedAccident).modelVersion" size="small" effect="plain">
              {{ (drawerDetail || selectedAccident).modelVersion }}
            </el-tag>
          </div>
          <div class="drawer-algorithm-grid">
            <div>
              <span>风险评分</span>
              <strong>{{ formatRiskScore((drawerDetail || selectedAccident).riskScore) }}</strong>
            </div>
            <div>
              <span>人流强度</span>
              <strong>{{ (drawerDetail || selectedAccident).peopleFlow || '-' }}</strong>
            </div>
            <div>
              <span>路面状况</span>
              <strong>{{ (drawerDetail || selectedAccident).roadStatus || '-' }}</strong>
            </div>
            <div>
              <span>追踪编号</span>
              <code>{{ (drawerDetail || selectedAccident).dataModuleTraceId || '-' }}</code>
            </div>
          </div>
          <div v-if="(drawerDetail || selectedAccident).riskFactors" class="drawer-factor-tags">
            <el-tag
              v-for="factor in splitRiskFactors((drawerDetail || selectedAccident).riskFactors)"
              :key="factor"
              size="small"
              type="warning"
              effect="plain"
            >
              {{ factor }}
            </el-tag>
          </div>
          <p v-if="(drawerDetail || selectedAccident).evidenceSummary" class="drawer-evidence">
            {{ (drawerDetail || selectedAccident).evidenceSummary }}
          </p>
        </div>

        <!-- 事故描述（列表数据已有） -->
        <div v-if="selectedAccident.description" class="drawer-section">
          <h4 class="drawer-section-title">事故描述</h4>
          <p class="drawer-section-text">{{ selectedAccident.description }}</p>
        </div>

        <div v-if="drawerDetail?.media?.length" class="drawer-section">
          <h4 class="drawer-section-title">带框现场媒体</h4>
          <div v-if="drawerDetail.media.some(item => item.hasAnnotatedMedia)" class="drawer-media">
            <template
              v-for="item in drawerDetail.media.filter(item => item.hasAnnotatedMedia)"
              :key="item.id"
            >
              <el-image
                v-if="item.type === 'PHOTO'"
                :src="item.url"
                :preview-src-list="drawerDetail.media.filter(i => i.hasAnnotatedMedia && i.type === 'PHOTO').map(i => i.url)"
                fit="cover"
                class="drawer-image"
              />
              <video
                v-else-if="item.type === 'VIDEO'"
                :src="item.url"
                controls
                class="drawer-video"
              ></video>
            </template>
          </div>
          <el-alert
            v-else
            title="暂无带框检测结果，请确认 YOLO 检测服务已返回 output_image_url / output_video_url"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <!-- 处置反馈（从详情接口获取） -->
        <div v-if="drawerDetail?.dispatchFeedback" class="drawer-section">
          <h4 class="drawer-section-title">
            <el-tag size="small" type="success" effect="plain" style="margin-right:6px;">反馈</el-tag>
            处置反馈
          </h4>
          <p class="drawer-section-text feedback-text">{{ drawerDetail.dispatchFeedback }}</p>
        </div>
        <div v-else-if="drawerDetailLoading" class="drawer-section">
          <p class="text-muted" style="text-align:center;padding:8px;">加载处置反馈...</p>
        </div>
      </template>

      <template v-else-if="drawerAccident">
        <div class="drawer-hero">
          <div>
            <span class="drawer-eyebrow">INCIDENT DETAIL</span>
            <h3>{{ drawerAccident.caseNo || `事故 #${drawerAccident.id}` }}</h3>
          </div>
          <RiskBadge :level="drawerAccident.riskLevel" />
        </div>

        <div v-if="drawerDetailLoading" class="drawer-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在加载完整事故信息…</span>
        </div>

        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="事故类型">{{ drawerAccident.type }}</el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ drawerAccident.riskScore }}</el-descriptions-item>
          <el-descriptions-item label="处理状态">
            <span class="status-chip" :class="statusClass(drawerAccident.status)">{{ drawerAccident.status }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="事故地点">{{ drawerAccident.location?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上报时间">{{ drawerAccident.reportTime }}</el-descriptions-item>
          <el-descriptions-item label="预计拥堵">{{ drawerAccident.congestionDuration }}</el-descriptions-item>
          <el-descriptions-item label="恢复时间">{{ drawerAccident.recoveryTime }}</el-descriptions-item>
          <el-descriptions-item label="影响车道">{{ drawerAccident.affectedLanes }}</el-descriptions-item>
          <el-descriptions-item label="天气 / 道路">{{ drawerAccident.weather }} / {{ drawerAccident.roadLevel }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-algorithm-panel">
          <div class="drawer-algorithm-head">
            <h4>算法2风险评估</h4>
            <el-tag v-if="drawerAccident.modelVersion" size="small" effect="plain">
              {{ drawerAccident.modelVersion }}
            </el-tag>
          </div>
          <div class="drawer-algorithm-grid">
            <div>
              <span>风险评分</span>
              <strong>{{ formatRiskScore(drawerAccident.riskScore) }}</strong>
            </div>
            <div>
              <span>人流强度</span>
              <strong>{{ drawerAccident.peopleFlow || '-' }}</strong>
            </div>
            <div>
              <span>路面状况</span>
              <strong>{{ drawerAccident.roadStatus || '-' }}</strong>
            </div>
            <div>
              <span>追踪编号</span>
              <code>{{ drawerAccident.dataModuleTraceId || '-' }}</code>
            </div>
          </div>
          <div v-if="drawerAccident.riskFactors" class="drawer-factor-tags">
            <el-tag
              v-for="factor in splitRiskFactors(drawerAccident.riskFactors)"
              :key="factor"
              size="small"
              type="warning"
              effect="plain"
            >
              {{ factor }}
            </el-tag>
          </div>
          <p v-if="drawerAccident.evidenceSummary" class="drawer-evidence">{{ drawerAccident.evidenceSummary }}</p>
        </div>

        <div v-if="drawerAccident.description" class="drawer-section">
          <h4>事故描述</h4>
          <p>{{ drawerAccident.description }}</p>
        </div>
        <div v-if="drawerAccident.disposalAdvice" class="drawer-section is-advice">
          <h4>智能处置建议</h4>
          <p>{{ drawerAccident.disposalAdvice }}</p>
        </div>
        <div v-if="drawerAccident.dispatchFeedback" class="drawer-section is-feedback">
          <h4>处置反馈</h4>
          <p>{{ drawerAccident.dispatchFeedback }}</p>
        </div>
      </template>

      <template #footer>
        <div class="drawer-footer-actions">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button
            v-if="drawerMode === 'accident' && drawerAccident && !['已处理', '已结案'].includes(drawerAccident.status)"
            type="success"
            @click="openDispatchDialog"
          >
            分配清障人员
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 地图事故抽屉内创建调度任务 -->
    <el-dialog
      v-model="dispatchDialogVisible"
      class="command-dispatch-dialog"
      title="分配调度任务"
      width="560px"
      :close-on-click-modal="false"
      @closed="resetDispatchForm"
    >
      <el-form
        ref="formRef"
        :model="dispatchForm"
        :rules="dispatchFormRules"
        label-position="top"
      >
        <el-form-item label="关联事故">
          <el-input :model-value="drawerAccident?.caseNo || ''" disabled />
        </el-form-item>

        <el-form-item label="调度人员" prop="receiverUserId">
          <el-select
            v-model="dispatchForm.receiverUserId"
            :loading="respondersLoading"
            placeholder="选择清障/救援人员"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="user in rescueUsers"
              :key="user.id"
              :label="`${user.fullName}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="车辆类型" prop="vehicleType">
          <el-select
            v-model="dispatchForm.vehicleType"
            placeholder="选择车辆类型后查看 ETA 排序"
            style="width: 100%"
            @change="handleVehicleTypeChange"
          >
            <el-option label="救护车" value="AMBULANCE" />
            <el-option label="清障车" value="CLEARANCE_TRUCK" />
          </el-select>
        </el-form-item>

        <el-form-item label="调度车辆" prop="vehicleId">
          <div class="selected-vehicle-box">
            <template v-if="selectedDispatchVehicle">
              <div class="selected-vehicle-main">
                <div class="selected-vehicle-title">
                  <strong>{{ selectedDispatchVehicle.vehicleNo }}</strong>
                  <el-tag
                    v-if="selectedDispatchVehicle.fastest"
                    size="small"
                    type="success"
                    effect="dark"
                  >
                    ETA 最快
                  </el-tag>
                </div>
                <div class="selected-vehicle-meta">
                  {{ selectedDispatchVehicle.vehicleName || selectedDispatchVehicle.vehicleTypeLabel }}
                  · 距离 {{ formatDistance(selectedDispatchVehicle.distanceKm) }}
                  · 预计 {{ formatEta(selectedDispatchVehicle.estimatedArrivalMinutes) }}到达
                </div>
              </div>
              <el-button type="primary" link @click="openEtaDialog">重新选择</el-button>
            </template>

            <template v-else>
              <span class="selected-vehicle-empty">
                {{ dispatchForm.vehicleType ? '尚未选择车辆' : '请先选择车辆类型' }}
              </span>
              <el-button
                type="primary"
                plain
                :disabled="!dispatchForm.vehicleType"
                @click="openEtaDialog"
              >
                查看 ETA 排序
              </el-button>
            </template>
          </div>
        </el-form-item>

        <el-form-item label="备注说明">
          <el-input
            v-model="dispatchForm.notes"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="填写现场注意事项或特殊要求"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dispatchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitDispatch">
          确认分配
        </el-button>
      </template>
    </el-dialog>

    <!-- 车辆 ETA 排序选择弹窗 -->
    <el-dialog
      v-model="etaDialogVisible"
      class="command-eta-dialog"
      title="按 ETA 选择调度车辆"
      width="920px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="eta-dialog-toolbar">
        <div>
          <div class="eta-dialog-title">
            {{ drawerAccident?.caseNo || '当前事故' }} · {{ vehicleTypeLabel(dispatchForm.vehicleType) }}
          </div>
          <div class="eta-dialog-tip">
            可用车辆已按预计到达时间从短到长排序，请由指挥人员确认最终车辆。
          </div>
        </div>
        <el-button type="primary" link :loading="etaLoading" @click="loadVehicleEtas">
          刷新 ETA
        </el-button>
      </div>

      <el-table
        v-loading="etaLoading"
        :data="etaList"
        stripe
        border
        height="420"
        highlight-current-row
        empty-text="当前没有可调度车辆"
        :row-class-name="etaRowClassName"
        @row-click="handleEtaRowClick"
      >
        <el-table-column label="选择" width="70" align="center">
          <template #default="{ row }">
            <el-radio
              v-model="etaSelectedVehicleId"
              :value="row.vehicleId"
              aria-label="选择车辆"
              @click.stop
            >
              <span />
            </el-radio>
          </template>
        </el-table-column>

        <el-table-column label="排名" width="72" align="center">
          <template #default="{ $index, row }">
            <span class="eta-rank" :class="{ 'is-fastest': row.fastest || $index === 0 }">
              {{ $index + 1 }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="车辆" min-width="190">
          <template #default="{ row }">
            <div class="eta-vehicle-cell">
              <div class="eta-vehicle-name">
                {{ row.vehicleName || row.vehicleTypeLabel }}
                <el-tag
                  v-if="row.fastest"
                  size="small"
                  type="success"
                  effect="dark"
                >
                  最快
                </el-tag>
              </div>
              <span>{{ row.vehicleNo || '-' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small" effect="plain">
              {{ row.statusLabel || '可调度' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="距离" width="110" align="right">
          <template #default="{ row }">
            {{ formatDistance(row.distanceKm) }}
          </template>
        </el-table-column>

        <el-table-column label="参考速度" width="110" align="right">
          <template #default="{ row }">
            {{ formatSpeed(row.speedKmh) }}
          </template>
        </el-table-column>

        <el-table-column label="预计到达" width="132" align="center">
          <template #default="{ row }">
            <strong class="eta-time-value">
              {{ formatEta(row.estimatedArrivalMinutes) }}
            </strong>
          </template>
        </el-table-column>

        <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
      </el-table>

      <div v-if="selectedEtaVehicle" class="eta-selected-summary">
        已选择 <strong>{{ selectedEtaVehicle.vehicleNo }}</strong>，距离
        {{ formatDistance(selectedEtaVehicle.distanceKm) }}，预计
        {{ formatEta(selectedEtaVehicle.estimatedArrivalMinutes) }}到达。
      </div>

      <template #footer>
        <el-button @click="etaDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!selectedEtaVehicle"
          @click="confirmEtaVehicle"
        >
          确认选择该车辆
        </el-button>
      </template>
    </el-dialog>

    <!-- YOLO 分析后现场媒体查看弹窗 -->
    <el-dialog
      v-model="mediaDialogVisible"
      class="command-media-dialog"
      :title="mediaTitle"
      width="860px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      @closed="clearMediaObjectUrls"
    >
      <div v-loading="mediaLoading" class="media-dialog-body">
        <template v-if="!mediaLoading && mediaItems.length">
          <div class="media-grid">
            <div
              v-for="(item, index) in mediaItems"
              :key="item.id"
              class="media-card"
            >
              <!-- YOLO 标注照片 -->
              <template v-if="item.attachmentType === 'PHOTO'">
                <el-image
                  :src="item.displayUrl"
                  :preview-src-list="mediaItems.filter(i => i.attachmentType === 'PHOTO' && i.displayUrl).map(i => i.displayUrl)"
                  :initial-index="index"
                  fit="cover"
                  class="media-thumb"
                >
                  <template #placeholder>
                    <div class="media-placeholder"><el-icon :size="32"><PictureFilled /></el-icon></div>
                  </template>
                  <template #error>
                    <div class="media-placeholder">
                      <el-icon :size="32"><PictureFilled /></el-icon>
                      <span>加载失败</span>
                    </div>
                  </template>
                </el-image>
                <div class="media-label">
                  <el-tag size="small" type="success" effect="plain">{{ item.hasAnnotatedMedia ? '📷 YOLO 标注照片' : '📷 原始照片' }}</el-tag>
                </div>
              </template>

              <!-- YOLO 标注视频 -->
              <template v-else-if="item.attachmentType === 'VIDEO'">
                <video
                  :key="`${item.id}-${item.displayUrl}`"
                  :src="item.displayUrl"
                  controls
                  preload="metadata"
                  playsinline
                  class="media-video"
                  @error="(e) => e.target.classList.add('is-error')"
                >
                  当前浏览器不支持该视频格式
                </video>
                <div class="media-label">
                  <el-tag size="small" :type="item.hasAnnotatedMedia ? 'warning' : 'info'" effect="plain">{{ item.hasAnnotatedMedia ? '🎬 YOLO 标注视频' : '🎬 原始视频（未返回标注结果）' }}</el-tag>
                </div>
              </template>

              <!-- AI 检测标签 -->
              <div v-if="item.aiDetectedTypes" class="media-ai-tags">
                <el-tag
                  v-for="type in item.aiDetectedTypes.split(',').map(t => t.trim()).filter(Boolean)"
                  :key="type"
                  size="small"
                  effect="dark"
                  round
                >{{ type }}</el-tag>
              </div>

              <div v-if="!item.hasAnnotatedMedia" class="media-warning">
                未拿到 YOLO 标注文件路径，请检查接口是否返回 annotatedFileUrl / output_video_url
              </div>
            </div>
          </div>
        </template>

        <div v-else-if="!mediaLoading" class="media-empty">
          <el-icon :size="48"><PictureFilled /></el-icon>
          <p>暂无可展示的 AI 分析媒体文件</p>
          <span>请确认后端返回 annotatedFileUrl，或在 aiDetectionJson 中返回 output_image_url / output_video_url</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="mediaDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAccidentStore } from '@/stores/accident'
import { useDispatchStore } from '@/stores/dispatch'
import { useUserStore } from '@/stores/user'
import { getAccidentList, getAccidentDetail } from '@/services/modules/accident'
import {
  dispatchSelectedVehicle,
  getDispatchList,
  getIncidentAttachmentBlobUrl,
  getIncidentAttachments,
  getResponders,
  getVehicleEtas,
} from '@/services/modules/dispatch'
import { wgs84ToBd09 } from '@/utils/location'
import { ElMessage, ElMessageBox } from 'element-plus'
import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'
import CommandSemiGauge from '@/components/CommandSemiGauge.vue'
import {
  Aim,
  ArrowDown,
  DArrowLeft,
  DArrowRight,
  Filter,
  Loading,
  Location,
  PictureFilled,
  Refresh,
  Search,
  SwitchButton,
  User,
  UserFilled,
  Van,
  VideoCamera,
} from '@element-plus/icons-vue'

const router = useRouter()
const accidentStore = useAccidentStore()
const dispatchStore = useDispatchStore()
const userStore = useUserStore()

const mapRef = ref(null)
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const advancedVisible = ref(false)
const refreshing = ref(false)
const taskLoading = ref(false)
const lastUpdatedTime = ref('')

// ====== 分析后现场媒体弹窗 ======
const mediaDialogVisible = ref(false)
const mediaLoading = ref(false)
const mediaItems = ref([])
const mediaTitle = ref('')
const mediaObjectUrls = ref([])

function clearMediaObjectUrls() {
  mediaObjectUrls.value.forEach((url) => URL.revokeObjectURL(url))
  mediaObjectUrls.value = []
}

async function hydrateMediaDisplayUrls(incidentId, items) {
  return Promise.all(items.map(async (item) => {
    // 有 YOLO 标注文件时，直接展示 /runs/api/... 静态资源
    if (item.annotatedFileUrl) {
      return {
        ...item,
        displayUrl: item.annotatedFileUrl,
        hasAnnotatedMedia: true,
      }
    }

    // 没有标注文件时，只能兜底展示原始附件。
    // 这里不能直接把 /api/v1/.../file 放进 <video>/<img>，因为媒体标签不会带 JWT。
    try {
      const blobUrl = await getIncidentAttachmentBlobUrl(incidentId, item.id)
      mediaObjectUrls.value.push(blobUrl)
      return {
        ...item,
        displayUrl: blobUrl,
        hasAnnotatedMedia: false,
      }
    } catch (error) {
      console.warn('[command] 原始媒体兜底加载失败:', error)
      return {
        ...item,
        displayUrl: item.fileUrl,
        hasAnnotatedMedia: false,
      }
    }
  }))
}

async function openMediaDialog(incident) {
  if (!incident?.id) return
  clearMediaObjectUrls()
  mediaTitle.value = `${incident.caseNo || '#' + incident.id} YOLO 分析现场媒体`
  mediaItems.value = []
  mediaLoading.value = true
  mediaDialogVisible.value = true
  try {
    const res = await getIncidentAttachments(incident.id)
    const visibleItems = (res.data || []).filter(
      (item) => item.attachmentType === 'PHOTO' || item.attachmentType === 'VIDEO'
    )
    mediaItems.value = await hydrateMediaDisplayUrls(incident.id, visibleItems)

    if (!mediaItems.value.length) {
      ElMessage.info('该事故暂无可展示的 AI 分析媒体文件')
      return
    }

    const missingAnnotatedCount = mediaItems.value.filter((item) => !item.hasAnnotatedMedia).length
    if (missingAnnotatedCount) {
      ElMessage.warning('部分媒体未返回 YOLO 标注文件路径，已临时展示原始媒体')
    }
  } catch (error) {
    ElMessage.error('加载分析媒体失败：' + (error.message || '未知错误'))
    mediaDialogVisible.value = false
  } finally {
    mediaLoading.value = false
  }
}

// ====== 顶部时间 ======
const now = ref(new Date())
let clockTimer = null
const currentTime = computed(() => now.value.toLocaleTimeString('zh-CN', { hour12: false }))
const currentDate = computed(() => now.value.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }))
const currentWeekday = computed(() => now.value.toLocaleDateString('zh-CN', { weekday: 'long' }))

// ====== 全量数据 ======
const allAccidents = computed(() => accidentStore.accidentList || [])
const taskList = ref([])
const accidentById = computed(() => new Map(allAccidents.value.map((item) => [Number(item.id), item])))

function relatedAccident(task) {
  if (!task?.accidentId) return null
  return accidentById.value.get(Number(task.accidentId)) || null
}

async function fetchAllAccidents() {
  if (accidentStore.loading) return
  accidentStore.loading = true
  try {
    const pageSize = 200
    let page = 1
    let total = Infinity
    const records = []
    while (records.length < total && page <= 10) {
      const res = await getAccidentList({ page, pageSize })
      if (res.code !== 200) break
      const batch = res.data?.list || []
      total = Number(res.data?.total ?? batch.length)
      records.push(...batch)
      if (!batch.length || batch.length < pageSize) break
      page += 1
    }
    accidentStore.setAccidents(records)
  } catch (error) {
    ElMessage.error('事故数据加载失败：' + (error.message || '未知错误'))
  } finally {
    accidentStore.loading = false
  }
}

async function fetchAllTasks() {
  if (taskLoading.value) return
  taskLoading.value = true
  try {
    const pageSize = 200
    let page = 1
    let total = Infinity
    const records = []
    while (records.length < total && page <= 10) {
      const res = await getDispatchList({ page, pageSize })
      if (res.code !== 200) break
      const batch = res.data?.list || []
      total = Number(res.data?.total ?? batch.length)
      records.push(...batch)
      if (!batch.length || batch.length < pageSize) break
      page += 1
    }
    taskList.value = records
    dispatchStore.setTasks(records)
  } catch (error) {
    ElMessage.error('调度历史加载失败：' + (error.message || '未知错误'))
  } finally {
    taskLoading.value = false
  }
}

async function refreshAll() {
  if (refreshing.value) return
  refreshing.value = true
  await Promise.all([fetchAllAccidents(), fetchAllTasks()])
  lastUpdatedTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  refreshing.value = false
}

// ====== 图表与联动 ======
const activeChartFilter = reactive({ dimension: '', value: '' })

function countBy(list, field) {
  const result = new Map()
  list.forEach((item) => {
    const value = item?.[field] || '其他'
    result.set(value, (result.get(value) || 0) + 1)
  })
  return Array.from(result.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
}

/** 按 sceneLabels 数组展开统计每个标签出现次数 */
function countBySceneLabels(list) {
  const result = new Map()
  list.forEach((item) => {
    const labels = item?.sceneLabels || []
    labels.forEach((label) => {
      if (label) result.set(label, (result.get(label) || 0) + 1)
    })
  })
  return Array.from(result.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
}

const sceneChartSource = computed(() => {
  if (activeChartFilter.dimension === 'status' && activeChartFilter.value) {
    return allAccidents.value.filter((item) => item.status === activeChartFilter.value)
  }
  return allAccidents.value
})

const typeChartSource = computed(() => {
  if (activeChartFilter.dimension === 'status' && activeChartFilter.value) {
    return allAccidents.value.filter((item) => item.status === activeChartFilter.value)
  }
  if (activeChartFilter.dimension === 'scene' && activeChartFilter.value) {
    return allAccidents.value.filter((item) => (item.sceneLabels || []).includes(activeChartFilter.value))
  }
  return allAccidents.value
})

const statusChartSource = computed(() => {
  if (activeChartFilter.dimension === 'type' && activeChartFilter.value) {
    return allAccidents.value.filter((item) => item.type === activeChartFilter.value)
  }
  if (activeChartFilter.dimension === 'scene' && activeChartFilter.value) {
    return allAccidents.value.filter((item) => (item.sceneLabels || []).includes(activeChartFilter.value))
  }
  return allAccidents.value
})

const sceneChartData = computed(() => countBySceneLabels(sceneChartSource.value))
const typeChartData = computed(() => countBy(typeChartSource.value, 'type'))
const statusChartData = computed(() => countBy(statusChartSource.value, 'status'))
const accidentTypeOptions = computed(() => Array.from(new Set(allAccidents.value.map((item) => item.type).filter(Boolean))).sort())

function handleChartSelect(dimension, value) {
  if (activeChartFilter.dimension === dimension && activeChartFilter.value === value) {
    activeChartFilter.dimension = ''
    activeChartFilter.value = ''
    return
  }
  activeChartFilter.dimension = dimension
  activeChartFilter.value = value
}

function matchesChartFilter(accident) {
  if (!activeChartFilter.value) return true
  if (activeChartFilter.dimension === 'scene') {
    return (accident.sceneLabels || []).includes(activeChartFilter.value)
  }
  return activeChartFilter.dimension === 'type'
    ? accident.type === activeChartFilter.value
    : accident.status === activeChartFilter.value
}

// ====== 事故查询 ======
const emptyFilters = () => ({ keyword: '', caseNo: '', status: '', riskLevel: '', type: '', dateRange: null })
const draftFilters = reactive(emptyFilters())
const appliedFilters = reactive(emptyFilters())

function applyIncidentFilters() {
  Object.assign(appliedFilters, {
    ...draftFilters,
    dateRange: draftFilters.dateRange ? [...draftFilters.dateRange] : null,
  })
}

function resetIncidentFilters() {
  Object.assign(draftFilters, emptyFilters())
  Object.assign(appliedFilters, emptyFilters())
}

const queryActiveCount = computed(() => {
  return ['keyword', 'caseNo', 'status', 'riskLevel', 'type'].filter((key) => Boolean(appliedFilters[key])).length
    + (appliedFilters.dateRange?.length === 2 ? 1 : 0)
})

function normalizeText(value) {
  return String(value || '').trim().toLowerCase()
}

function matchesAppliedFilters(item) {
  const keyword = normalizeText(appliedFilters.keyword)
  if (keyword) {
    const haystack = [item.caseNo, item.type, item.description, item.location?.name, item.location?.road, item.location?.area]
      .map(normalizeText)
      .join(' ')
    if (!haystack.includes(keyword)) return false
  }
  if (appliedFilters.caseNo && !normalizeText(item.caseNo).includes(normalizeText(appliedFilters.caseNo))) return false
  if (appliedFilters.status && item.status !== appliedFilters.status) return false
  if (appliedFilters.riskLevel && item.riskLevel !== appliedFilters.riskLevel) return false
  if (appliedFilters.type && item.type !== appliedFilters.type) return false
  if (appliedFilters.dateRange?.length === 2) {
    const report = new Date(item.reportTime).getTime()
    const start = new Date(appliedFilters.dateRange[0]).setHours(0, 0, 0, 0)
    const end = new Date(appliedFilters.dateRange[1]).setHours(23, 59, 59, 999)
    if (!Number.isFinite(report) || report < start || report > end) return false
  }
  return true
}

function sortByTimeDesc(list, field) {
  return [...list].sort((a, b) => {
    const left = new Date(a?.[field] || 0).getTime() || 0
    const right = new Date(b?.[field] || 0).getTime() || 0
    return right - left
  })
}

const filteredAccidents = computed(() => sortByTimeDesc(
  allAccidents.value.filter(matchesChartFilter).filter(matchesAppliedFilters),
  'reportTime'
))

const filteredTasks = computed(() => {
  let list = taskList.value
  if (activeChartFilter.value) {
    list = list.filter((task) => {
      const accident = relatedAccident(task)
      if (activeChartFilter.dimension === 'type') {
        return (task.accidentType || accident?.type) === activeChartFilter.value
      }
      if (activeChartFilter.dimension === 'scene') {
        return (accident?.sceneLabels || []).includes(activeChartFilter.value)
      }
      return accident?.status === activeChartFilter.value
    })
  }
  return sortByTimeDesc(list, 'createTime')
})

// ====== 地图标记与主动定位 ======
const mapMarkers = computed(() =>
  filteredAccidents.value
    .filter((item) => Number(item.location?.lng) && Number(item.location?.lat))
    .slice(0, 100)
    .map((item) => {
      const point = wgs84ToBd09(Number(item.location.lng), Number(item.location.lat))
      return {
        lng: point.lng,
        lat: point.lat,
        label: item.caseNo || item.type,
        onClick: () => openAccident(item),
      }
    })
)

function focusAccidentOnMap(accident) {
  if (!accident?.location?.lng || !accident?.location?.lat) return
  const point = wgs84ToBd09(Number(accident.location.lng), Number(accident.location.lat))
  nextTick(() => mapRef.value?.focusPoint(point.lng, point.lat, 17))
}

function focusTaskOnMap(task) {
  const accident = relatedAccident(task)
  if (accident) {
    focusAccidentOnMap(accident)
    return
  }
  if (task?.location?.lng && task?.location?.lat) {
    const point = wgs84ToBd09(Number(task.location.lng), Number(task.location.lat))
    nextTick(() => mapRef.value?.focusPoint(point.lng, point.lat, 17))
  }
}

// ====== 统一详情抽屉 ======
const drawerVisible = ref(false)
const drawerMode = ref('accident')
const selectedAccidentId = ref(null)
const selectedTask = ref(null)
const drawerDetail = ref(null)
const drawerDetailLoading = ref(false)

const selectedAccident = computed(() => accidentById.value.get(Number(selectedAccidentId.value)) || null)
const drawerAccident = computed(() => drawerDetail.value || selectedAccident.value)

async function loadAccidentDetail(id) {
  drawerDetailLoading.value = true
  drawerDetail.value = null
  try {
    const res = await getAccidentDetail(id)
    if (res.code === 200) drawerDetail.value = res.data
  } catch {
    // 列表数据仍可用于抽屉展示
  } finally {
    drawerDetailLoading.value = false
  }
}

function openAccident(accident) {
  if (!accident) return
  drawerMode.value = 'accident'
  selectedTask.value = null
  selectedAccidentId.value = accident.id
  drawerVisible.value = true
  focusAccidentOnMap(accident)
  loadAccidentDetail(accident.id)
}

function openTask(task) {
  if (!task) return
  drawerMode.value = 'task'
  selectedTask.value = task
  const accident = relatedAccident(task)
  selectedAccidentId.value = accident?.id || task.accidentId || null
  drawerVisible.value = true
  drawerDetail.value = null
  focusTaskOnMap(task)
  if (selectedAccidentId.value) {
    loadAccidentDetail(selectedAccidentId.value)
  }
}

function formatRiskScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  const value = Number(score)
  if (Number.isNaN(value)) return score
  return value.toFixed(1)
}

function splitRiskFactors(value) {
  if (!value) return []
  return String(value)
    .split(/[、,，;；]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

// ====== 抽屉内 ETA 车辆调度 ======
const dispatchDialogVisible = ref(false)
const etaDialogVisible = ref(false)
const creating = ref(false)
const etaLoading = ref(false)
const respondersLoading = ref(false)
const formRef = ref(null)

const rescueUsers = ref([])
const etaList = ref([])
const etaSelectedVehicleId = ref(null)

const dispatchForm = reactive({
  receiverUserId: null,
  vehicleType: '',
  vehicleId: null,
  notes: '',
})

const selectedDispatchVehicle = computed(() =>
  etaList.value.find((item) => Number(item.vehicleId) === Number(dispatchForm.vehicleId)) || null
)

const selectedEtaVehicle = computed(() =>
  etaList.value.find((item) => Number(item.vehicleId) === Number(etaSelectedVehicleId.value)) || null
)

const dispatchFormRules = {
  receiverUserId: [{ required: true, message: '请选择调度人员', trigger: 'change' }],
  vehicleType: [{ required: true, message: '请选择车辆类型', trigger: 'change' }],
  vehicleId: [{ required: true, message: '请根据 ETA 排序选择调度车辆', trigger: 'change' }],
}

function resetDispatchForm() {
  Object.assign(dispatchForm, {
    receiverUserId: null,
    vehicleType: '',
    vehicleId: null,
    notes: '',
  })
  etaList.value = []
  etaSelectedVehicleId.value = null
  etaDialogVisible.value = false
  nextTick(() => formRef.value?.clearValidate())
}

async function fetchResponders() {
  if (respondersLoading.value || rescueUsers.value.length) return
  respondersLoading.value = true
  try {
    const res = await getResponders('RESCUE_WORKER')
    rescueUsers.value = res.data || []
  } catch (error) {
    ElMessage.error('调度人员加载失败：' + (error.message || '未知错误'))
  } finally {
    respondersLoading.value = false
  }
}

async function openDispatchDialog() {
  if (!drawerAccident.value) return
  resetDispatchForm()
  dispatchDialogVisible.value = true
  await fetchResponders()
}

function handleVehicleTypeChange(vehicleType) {
  dispatchForm.vehicleId = null
  etaSelectedVehicleId.value = null
  etaList.value = []
  formRef.value?.clearValidate('vehicleId')
  if (!vehicleType) return
  openEtaDialog()
}

async function openEtaDialog() {
  if (!dispatchForm.vehicleType) {
    ElMessage.warning('请先选择车辆类型')
    return
  }
  if (!drawerAccident.value?.id) {
    ElMessage.warning('当前事故信息不完整，无法查询车辆 ETA')
    return
  }
  etaSelectedVehicleId.value = dispatchForm.vehicleId
  etaDialogVisible.value = true
  await loadVehicleEtas()
}

async function loadVehicleEtas() {
  if (!drawerAccident.value?.id || !dispatchForm.vehicleType || etaLoading.value) return
  etaLoading.value = true
  try {
    const res = await getVehicleEtas(drawerAccident.value.id, dispatchForm.vehicleType)
    etaList.value = [...(res.data || [])].sort((left, right) => {
      const leftEta = Number(left.estimatedArrivalMinutes)
      const rightEta = Number(right.estimatedArrivalMinutes)
      const safeLeft = Number.isFinite(leftEta) ? leftEta : Number.MAX_SAFE_INTEGER
      const safeRight = Number.isFinite(rightEta) ? rightEta : Number.MAX_SAFE_INTEGER
      return safeLeft - safeRight
    })

    if (!etaList.value.length) {
      etaSelectedVehicleId.value = null
      dispatchForm.vehicleId = null
      ElMessage.warning('当前没有具备坐标且状态为可调度的车辆')
      return
    }

    const selectedStillExists = etaList.value.some(
      (item) => Number(item.vehicleId) === Number(etaSelectedVehicleId.value)
    )
    if (!selectedStillExists) etaSelectedVehicleId.value = null
  } catch (error) {
    etaList.value = []
    etaSelectedVehicleId.value = null
    ElMessage.error('车辆 ETA 查询失败：' + (error.message || '未知错误'))
  } finally {
    etaLoading.value = false
  }
}

function handleEtaRowClick(row) {
  if (row?.vehicleId != null) etaSelectedVehicleId.value = row.vehicleId
}

function confirmEtaVehicle() {
  if (!selectedEtaVehicle.value) {
    ElMessage.warning('请选择一辆调度车辆')
    return
  }
  dispatchForm.vehicleId = selectedEtaVehicle.value.vehicleId
  etaDialogVisible.value = false
  formRef.value?.clearValidate('vehicleId')
}

function etaRowClassName({ row }) {
  return Number(row.vehicleId) === Number(etaSelectedVehicleId.value) ? 'is-selected-eta-row' : ''
}

function vehicleTypeLabel(value) {
  return {
    AMBULANCE: '救护车',
    CLEARANCE_TRUCK: '清障车',
  }[value] || value || '-'
}

function formatDistance(value) {
  const number = Number(value)
  return Number.isFinite(number) ? `${number.toFixed(1)} km` : '-'
}

function formatSpeed(value) {
  const number = Number(value)
  return Number.isFinite(number) ? `${number.toFixed(1)} km/h` : '-'
}

function formatEta(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '暂未计算'
  if (number <= 0) return '已到达'
  if (number < 60) return `${Math.ceil(number)} 分钟`
  const totalMinutes = Math.ceil(number)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return minutes ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

async function submitDispatch() {
  if (!formRef.value || !drawerAccident.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    if (dispatchForm.vehicleType && !dispatchForm.vehicleId) await openEtaDialog()
    return
  }

  if (!userStore.userInfo?.id) {
    ElMessage.error('当前登录用户缺少用户 ID，无法提交调度')
    return
  }

  const vehicle = selectedDispatchVehicle.value
  if (!vehicle) {
    ElMessage.warning('所选车辆已不在 ETA 列表中，请重新选择')
    await openEtaDialog()
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认调度 ${vehicle.vehicleNo} 前往事故 ${drawerAccident.value.caseNo || drawerAccident.value.id}？预计 ${formatEta(vehicle.estimatedArrivalMinutes)}到达。`,
      '确认车辆调度',
      {
        confirmButtonText: '确认调度',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  creating.value = true
  try {
    const res = await dispatchSelectedVehicle(drawerAccident.value.id, {
      vehicleType: dispatchForm.vehicleType,
      vehicleId: dispatchForm.vehicleId,
      receiverUserId: dispatchForm.receiverUserId,
      assignedByUserId: userStore.userInfo.id,
      advice: dispatchForm.notes,
    })
    if (res.code === 200) {
      ElMessage.success('车辆调度成功')
      dispatchDialogVisible.value = false
      await Promise.all([fetchAllAccidents(), fetchAllTasks()])
    }
  } catch (error) {
    ElMessage.error('创建调度任务失败：' + (error.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

// ====== 显示辅助 ======
function compactDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 16)
  const pad = (num) => String(num).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function statusClass(status) {
  return {
    '待处理': 'is-danger',
    '处理中': 'is-warning',
    '已处理': 'is-success',
    '已结案': 'is-muted',
  }[status] || 'is-muted'
}

function taskStatusClass(status) {
  return {
    '待接收': 'is-muted',
    '已出发': 'is-warning',
    '已到达': 'is-info',
    '处理中': 'is-primary',
    '已完成': 'is-success',
    '已取消': 'is-danger',
  }[status] || 'is-muted'
}

async function handleUserCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确认退出指挥中心？', '退出登录', {
        confirmButtonText: '确认退出',
        cancelButtonText: '取消',
        type: 'warning',
      })
      userStore.logout()
      router.push('/login')
    } catch {
      // 用户取消
    }
  }
}

// ====== 生命周期 ======
let pollTimer = null
onMounted(async () => {
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  await refreshAll()
  pollTimer = setInterval(refreshAll, 10000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (pollTimer) clearInterval(pollTimer)
  clearMediaObjectUrls()
})
</script>

<style lang="scss" scoped>
.command-center-page {
  --cyan: #00e5ff;
  --cyan-soft: rgba(0, 229, 255, 0.34);
  --panel-bg: rgba(3, 15, 31, 0.78);
  --panel-deep: rgba(2, 11, 24, 0.9);
  --line: rgba(80, 211, 255, 0.2);
  --text-main: #eafaff;
  --text-soft: #8fb4c4;
  --text-dim: #527486;
  --left-width: 320px;
  --right-width: 420px;

  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  color: var(--text-main);
  background: #020812;
  font-family: Inter, system-ui, -apple-system, sans-serif;
}

.map-stage,
.map-vignette,
.map-grid-overlay {
  position: absolute;
  inset: 0;
}

.map-stage { z-index: 0; }

.command-map {
  border: 0 !important;
  border-radius: 0 !important;
}

:deep(.command-map.baidu-map-card) {
  border: 0;
  border-radius: 0;
  background: #071426;
}

:deep(.command-map .map-container) {
  filter: saturate(0.92) contrast(1.06) brightness(0.82);
}

:deep(.command-map .map-overlay) {
  border: 0;
  border-radius: 0;
  background: radial-gradient(circle at center, rgba(7, 35, 62, 0.94), rgba(2, 8, 18, 0.98));
}

.map-vignette {
  z-index: 2;
  pointer-events: none;
  background:
    linear-gradient(90deg, rgba(1, 7, 16, 0.78) 0%, rgba(1, 8, 18, 0.18) 25%, transparent 43%, transparent 57%, rgba(1, 8, 18, 0.18) 75%, rgba(1, 7, 16, 0.78) 100%),
    linear-gradient(180deg, rgba(1, 8, 18, 0.78) 0%, transparent 14%, transparent 82%, rgba(1, 8, 18, 0.62) 100%);
}

.map-grid-overlay {
  z-index: 3;
  pointer-events: none;
  opacity: 0.32;
  background-image:
    linear-gradient(rgba(0, 229, 255, 0.045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 229, 255, 0.045) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(to right, #000 0%, transparent 30%, transparent 70%, #000 100%);
}

.command-header {
  position: absolute;
  z-index: 30;
  top: 0;
  left: 0;
  right: 0;
  height: 76px;
  display: grid;
  grid-template-columns: 1fr minmax(520px, 780px) 1fr;
  align-items: center;
  padding: 0 24px;
  background: linear-gradient(180deg, rgba(1, 8, 18, 0.96), rgba(1, 8, 18, 0.42), transparent);
  pointer-events: none;
}

.header-corner,
.center-title { pointer-events: auto; }

.header-time {
  justify-self: start;
  padding-left: 12px;
  border-left: 2px solid var(--cyan);
}

.time-value {
  font-family: 'DIN Alternate', 'JetBrains Mono', monospace;
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.08em;
  color: #f4fdff;
  text-shadow: 0 0 14px rgba(0, 229, 255, 0.5);
}

.date-value {
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-soft);
  letter-spacing: 0.09em;
}

.center-title {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  min-width: 0;
}

.title-copy {
  text-align: center;
  white-space: nowrap;

  h1 {
    margin: 0;
    font-size: 27px;
    line-height: 1.12;
    font-weight: 650;
    letter-spacing: 0.12em;
    color: #f2fdff;
    text-shadow: 0 0 20px rgba(0, 229, 255, 0.46);
  }

  p {
    margin: 7px 0 0;
    font-size: 9px;
    color: #5d91a6;
    letter-spacing: 0.28em;
  }
}

.title-line {
  position: relative;
  width: 88px;
  height: 1px;
  flex-shrink: 1;
  background: linear-gradient(90deg, transparent, var(--cyan));
  box-shadow: 0 0 10px rgba(0, 229, 255, 0.6);

  &::after {
    content: '';
    position: absolute;
    right: 0;
    top: -3px;
    width: 7px;
    height: 7px;
    transform: rotate(45deg);
    border: 1px solid var(--cyan);
    background: #051426;
  }

  &.is-right {
    transform: scaleX(-1);
  }
}

.header-user { justify-self: end; }

.user-console {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 4px 10px 4px 5px;
  color: var(--text-main);
  cursor: pointer;
  border: 1px solid rgba(0, 229, 255, 0.2);
  border-radius: 8px;
  background: rgba(3, 17, 34, 0.72);
  backdrop-filter: blur(12px);
  transition: all 0.2s ease;

  &:hover {
    border-color: rgba(0, 229, 255, 0.55);
    box-shadow: 0 0 18px rgba(0, 229, 255, 0.12);
  }

  :deep(.el-avatar) {
    color: var(--cyan);
    background: rgba(0, 229, 255, 0.12);
    border: 1px solid rgba(0, 229, 255, 0.32);
  }
}

.user-name { font-size: 12px; font-weight: 600; }
.user-role {
  padding: 2px 6px;
  border-radius: 3px;
  color: var(--cyan);
  font-size: 9px;
  letter-spacing: 0.06em;
  background: rgba(0, 229, 255, 0.1);
}

.side-rail {
  position: absolute;
  z-index: 20;
  top: 86px;
  bottom: 22px;
  display: grid;
  gap: 12px;
  transition: transform 0.35s cubic-bezier(0.22, 0.8, 0.2, 1), opacity 0.25s ease;
}

.left-rail {
  left: 18px;
  width: var(--left-width);
  grid-template-rows: repeat(2, minmax(0, 1fr));

  &.is-collapsed {
    opacity: 0;
    pointer-events: none;
    transform: translateX(calc(-100% - 24px));
  }
}

.right-rail {
  right: 18px;
  width: var(--right-width);
  grid-template-rows: minmax(0, 1.38fr) minmax(0, 0.92fr);

  &.is-collapsed {
    opacity: 0;
    pointer-events: none;
    transform: translateX(calc(100% + 24px));
  }
}

.hud-panel {
  position: relative;
  min-height: 0;
  overflow: hidden;
  border: 1px solid rgba(65, 205, 255, 0.22);
  background: linear-gradient(145deg, rgba(5, 22, 43, 0.86), rgba(2, 11, 24, 0.76));
  box-shadow:
    inset 0 0 36px rgba(0, 184, 255, 0.035),
    0 12px 40px rgba(0, 0, 0, 0.28),
    0 0 22px rgba(0, 229, 255, 0.055);
  backdrop-filter: blur(17px) saturate(1.15);

  &::before,
  &::after {
    content: '';
    position: absolute;
    z-index: 3;
    width: 20px;
    height: 20px;
    pointer-events: none;
  }

  &::before {
    top: -1px;
    left: -1px;
    border-top: 2px solid var(--cyan);
    border-left: 2px solid var(--cyan);
  }

  &::after {
    right: -1px;
    bottom: -1px;
    border-right: 2px solid var(--cyan);
    border-bottom: 2px solid var(--cyan);
  }
}

.panel-heading {
  position: relative;
  z-index: 2;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 18px;
  border-bottom: 1px solid rgba(67, 183, 222, 0.13);
  background: linear-gradient(90deg, rgba(0, 229, 255, 0.07), transparent 62%);

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 17px;
    width: 3px;
    height: 23px;
    background: var(--cyan);
    box-shadow: 0 0 12px var(--cyan);
  }

  h2 {
    margin: 0;
    color: #eafaff;
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 0.08em;
  }

  p {
    margin: 3px 0 0;
    color: #416e82;
    font-size: 8px;
    letter-spacing: 0.15em;
  }
}

.compact-heading { height: 54px; }

.panel-total,
.filter-count {
  flex-shrink: 0;
  padding: 3px 7px;
  color: #7bdff4;
  font-size: 9px;
  line-height: 1.2;
  letter-spacing: 0.06em;
  border: 1px solid rgba(0, 229, 255, 0.18);
  border-radius: 3px;
  background: rgba(0, 229, 255, 0.06);
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-action {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  color: #7db2c6;
  cursor: pointer;
  border: 1px solid rgba(0, 229, 255, 0.16);
  border-radius: 5px;
  background: rgba(0, 229, 255, 0.04);

  &:hover { color: var(--cyan); border-color: rgba(0, 229, 255, 0.45); }
}

.chart-panel {
  display: flex;
  flex-direction: column;
}

.chart-body {
  flex: 1;
  min-height: 0;
  padding: 0 6px 8px;
}

.rail-toggle {
  position: absolute;
  z-index: 25;
  top: 50%;
  width: 25px;
  height: 58px;
  display: grid;
  place-items: center;
  color: #77cfe3;
  cursor: pointer;
  border: 1px solid rgba(0, 229, 255, 0.24);
  background: rgba(3, 17, 34, 0.8);
  backdrop-filter: blur(10px);
  transition: all 0.35s cubic-bezier(0.22, 0.8, 0.2, 1);

  &:hover {
    color: #fff;
    border-color: var(--cyan);
    box-shadow: 0 0 18px rgba(0, 229, 255, 0.18);
  }
}

.left-toggle {
  left: calc(18px + var(--left-width));
  border-left: 0;
  border-radius: 0 6px 6px 0;

  &.is-collapsed { left: 0; }
}

.right-toggle {
  right: calc(18px + var(--right-width));
  border-right: 0;
  border-radius: 6px 0 0 6px;

  &.is-collapsed { right: 0; }
}

.incident-panel,
.dispatch-panel {
  display: flex;
  flex-direction: column;
}

.query-controls {
  flex-shrink: 0;
  padding: 10px 12px 8px;
  border-bottom: 1px solid rgba(67, 183, 222, 0.12);
  background: rgba(2, 11, 24, 0.35);
}

.quick-search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 7px;
  align-items: center;
}

.query-button { min-width: 52px; }

.advanced-button {
  height: 28px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  color: #8db7c8;
  cursor: pointer;
  border: 1px solid rgba(0, 229, 255, 0.18);
  border-radius: 5px;
  background: rgba(0, 229, 255, 0.045);
  font-size: 11px;

  &:hover { color: var(--cyan); border-color: rgba(0, 229, 255, 0.45); }
}

.advanced-arrow {
  font-size: 10px;
  transition: transform 0.2s ease;
  &.is-open { transform: rotate(180deg); }
}

.advanced-filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed rgba(0, 229, 255, 0.14);
}

.date-range-filter,
.advanced-actions { grid-column: 1 / -1; }

.date-range-filter { width: 100% !important; }

.advanced-actions {
  display: flex;
  justify-content: flex-end;
  gap: 14px;
  padding-top: 1px;
}

.text-action {
  padding: 0;
  color: #668c9d;
  cursor: pointer;
  border: 0;
  background: transparent;
  font-size: 10px;

  &:hover,
  &.is-primary { color: var(--cyan); }
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 7px;
  color: #53798a;
  font-size: 10px;

  strong { color: var(--cyan); font-size: 12px; }
}

.linked-filter {
  min-width: 0;
  overflow: hidden;
  color: #74cbe0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.query-controls .el-input__wrapper),
:deep(.query-controls .el-select__wrapper),
:deep(.query-controls .el-date-editor.el-input__wrapper) {
  min-height: 28px;
  color: #dffaff;
  border-radius: 5px;
  background: rgba(5, 25, 47, 0.84) !important;
  box-shadow: 0 0 0 1px rgba(73, 188, 226, 0.18) inset !important;

  &:hover { box-shadow: 0 0 0 1px rgba(0, 229, 255, 0.42) inset !important; }
  &.is-focus { box-shadow: 0 0 0 1px var(--cyan) inset, 0 0 12px rgba(0, 229, 255, 0.08) !important; }
}

:deep(.query-controls .el-input__inner),
:deep(.query-controls .el-range-input),
:deep(.query-controls .el-select__selected-item) {
  color: #dffaff !important;
  font-size: 11px;
}

:deep(.query-controls .el-input__inner::placeholder),
:deep(.query-controls .el-range-input::placeholder) { color: #54798a; }
:deep(.query-controls .el-range-separator),
:deep(.query-controls .el-input__icon),
:deep(.query-controls .el-select__caret) { color: #5c8799; }

.incident-list,
.dispatch-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 7px 8px 10px;
}

.hud-panel :deep(.el-loading-mask) {
  background: rgba(2, 11, 24, 0.72);
}

.hud-panel :deep(.el-loading-spinner .circular .path) { stroke: var(--cyan); }

.hud-scroll {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 229, 255, 0.28) transparent;

  &::-webkit-scrollbar { width: 4px; }
  &::-webkit-scrollbar-track { background: transparent; }
  &::-webkit-scrollbar-thumb { border-radius: 4px; background: rgba(0, 229, 255, 0.25); }
}

.incident-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 10px 10px 9px;
  cursor: pointer;
  border: 1px solid transparent;
  border-bottom-color: rgba(79, 161, 191, 0.11);
  background: linear-gradient(90deg, rgba(0, 229, 255, 0.025), transparent);
  transition: all 0.2s ease;

  &:hover,
  &.is-active {
    border-color: rgba(0, 229, 255, 0.27);
    background: linear-gradient(90deg, rgba(0, 229, 255, 0.1), rgba(0, 229, 255, 0.025));
    box-shadow: inset 3px 0 0 var(--cyan), 0 0 16px rgba(0, 229, 255, 0.05);
  }
}

.row-main { min-width: 0; }
.row-topline,
.dispatch-topline,
.dispatch-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.case-number {
  color: #dffaff;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.03em;
}

:deep(.incident-row .el-tag) {
  height: 18px;
  padding: 0 6px;
  line-height: 18px;
  font-size: 9px;
  border-radius: 3px;
}

.row-title {
  margin-top: 4px;
  color: #c5e4ed;
  font-size: 12px;
  font-weight: 500;
}

.row-location {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  margin-top: 4px;
  color: #62899a;
  font-size: 10px;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.row-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;

  time { color: #486e80; font-size: 9px; white-space: nowrap; }
}

.media-view-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  color: #6493a6;
  cursor: pointer;
  border: 1px solid rgba(0, 229, 255, 0.16);
  border-radius: 4px;
  background: rgba(0, 229, 255, 0.05);
  transition: all 0.2s ease;

  &:hover {
    color: var(--cyan);
    border-color: rgba(0, 229, 255, 0.5);
    background: rgba(0, 229, 255, 0.12);
    box-shadow: 0 0 8px rgba(0, 229, 255, 0.1);
  }

  .el-icon { font-size: 13px; }
}

.status-chip,
.task-status,
.drawer-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 6px;
  color: #819aaa;
  border: 1px solid currentColor;
  border-radius: 3px;
  background: rgba(129, 154, 170, 0.08);
  font-size: 9px;
  line-height: 1.2;
  white-space: nowrap;

  &.is-danger { color: #ff5d7d; background: rgba(255, 93, 125, 0.08); }
  &.is-warning { color: #ffb84d; background: rgba(255, 184, 77, 0.08); }
  &.is-success { color: #38e6a1; background: rgba(56, 230, 161, 0.08); }
  &.is-primary { color: #00e5ff; background: rgba(0, 229, 255, 0.08); }
  &.is-info { color: #76a9ff; background: rgba(118, 169, 255, 0.08); }
  &.is-muted { color: #7891a0; background: rgba(120, 145, 160, 0.08); }
}

.dispatch-row {
  position: relative;
  display: grid;
  grid-template-columns: 31px minmax(0, 1fr) 16px;
  gap: 9px;
  align-items: center;
  padding: 9px 8px;
  cursor: pointer;
  border: 1px solid transparent;
  border-bottom-color: rgba(79, 161, 191, 0.11);
  transition: all 0.2s ease;

  &:hover,
  &.is-active {
    border-color: rgba(0, 229, 255, 0.24);
    background: rgba(0, 229, 255, 0.065);
  }
}

.dispatch-icon {
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  color: #6fd7ed;
  border: 1px solid rgba(0, 229, 255, 0.18);
  border-radius: 5px;
  background: rgba(0, 229, 255, 0.07);
}

.dispatch-copy { min-width: 0; }
.dispatch-topline {
  color: #d9f4fa;
  font-family: 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 600;
}

.dispatch-title {
  margin-top: 4px;
  overflow: hidden;
  color: #9fc4d4;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;

  span { color: #557a8c; }
}

.dispatch-meta {
  margin-top: 4px;
  color: #4e7485;
  font-size: 9px;
}

.locate-arrow { color: #396477; font-size: 13px; }
.dispatch-row:hover .locate-arrow { color: var(--cyan); }

.empty-state {
  height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #42697a;
  font-size: 11px;

  .el-icon { font-size: 24px; }
}

.compact-empty { height: 80px; }

.map-status-strip {
  position: absolute;
  z-index: 15;
  left: 50%;
  bottom: 18px;
  display: flex;
  align-items: center;
  gap: 0;
  transform: translateX(-50%);
  color: #6992a3;
  border: 1px solid rgba(0, 229, 255, 0.15);
  border-radius: 4px;
  background: rgba(2, 11, 24, 0.72);
  box-shadow: 0 0 18px rgba(0, 0, 0, 0.24);
  backdrop-filter: blur(10px);
  font-size: 9px;
  white-space: nowrap;

  span {
    padding: 5px 11px;
    border-right: 1px solid rgba(0, 229, 255, 0.1);
    &:last-child { border-right: 0; }
  }

  strong { color: #c9f6ff; }
}

.online-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 6px;
  border-radius: 50%;
  background: #39e79f;
  box-shadow: 0 0 8px #39e79f;
  animation: status-pulse 1.8s ease-in-out infinite;
}

.drawer-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid rgba(0, 229, 255, 0.18);
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(0, 229, 255, 0.09), rgba(64, 84, 255, 0.04));

  h3 { margin: 4px 0 0; color: #edfaff; font-size: 17px; }
}

.drawer-eyebrow {
  color: #54869b;
  font-size: 9px;
  letter-spacing: 0.18em;
}

.drawer-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #6b98aa;
  font-size: 11px;
}

.drawer-section,
.drawer-link-card {
  margin-top: 14px;
  padding: 13px;
  border: 1px solid rgba(0, 229, 255, 0.13);
  border-radius: 7px;
  background: rgba(0, 229, 255, 0.035);

  h4 { margin: 0 0 7px; color: #a9d5e2; font-size: 12px; }
  p { margin: 0; color: #7da3b2; font-size: 11px; line-height: 1.75; white-space: pre-wrap; }

  &.is-advice { border-color: rgba(86, 119, 255, 0.2); background: rgba(86, 119, 255, 0.045); }
  &.is-feedback { border-color: rgba(56, 230, 161, 0.2); background: rgba(56, 230, 161, 0.045); }
}

.drawer-link-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;

  div { display: flex; flex-direction: column; gap: 4px; }
  span { color: #587e90; font-size: 9px; }
  strong { color: #bfeaf4; font-size: 11px; }
  .el-icon { color: var(--cyan); }

  &:hover { border-color: rgba(0, 229, 255, 0.4); }
}

.drawer-footer-actions { display: flex; justify-content: flex-end; gap: 10px; }

@keyframes status-pulse {
  0%, 100% { opacity: 0.55; transform: scale(0.88); }
  50% { opacity: 1; transform: scale(1.12); }
}

@media (max-width: 1550px) {
  .command-center-page {
    --left-width: 286px;
    --right-width: 378px;
  }

  .command-header { grid-template-columns: 1fr minmax(460px, 650px) 1fr; }
  .title-copy h1 { font-size: 22px; }
  .title-line { width: 56px; }
  .user-role { display: none; }
}

@media (max-height: 860px) {
  .command-header { height: 66px; }
  .side-rail { top: 72px; bottom: 14px; gap: 8px; }
  .panel-heading { height: 48px; }
  .panel-heading p { display: none; }
  .chart-body { padding-bottom: 2px; }
  :deep(.semi-gauge-chart) { min-height: 180px; }
  .query-controls { padding-top: 7px; padding-bottom: 5px; }
  .incident-row { padding-top: 7px; padding-bottom: 7px; }
  .dispatch-row { padding-top: 7px; padding-bottom: 7px; }
  .map-status-strip { bottom: 8px; }
}
</style>

<style lang="scss">
.command-detail-drawer.el-drawer {
  color: #dffaff;
  border-left: 1px solid rgba(0, 229, 255, 0.22);
  border-radius: 0;
  background: rgba(3, 15, 31, 0.97);
  box-shadow: -12px 0 48px rgba(0, 0, 0, 0.42), 0 0 24px rgba(0, 229, 255, 0.06);
  backdrop-filter: blur(18px);

  .el-drawer__header {
    color: #eafaff;
    border-bottom: 1px solid rgba(0, 229, 255, 0.12);
    padding-bottom: 14px;
  }

  .el-drawer__body { color: #b8dbe5; }
  .el-drawer__footer { border-top: 1px solid rgba(0, 229, 255, 0.1); padding-top: 14px; }

  .el-descriptions__body { background: transparent; }
  .el-descriptions__table { border-color: rgba(0, 229, 255, 0.13) !important; }
  .el-descriptions__cell { border-color: rgba(0, 229, 255, 0.13) !important; }
  .el-descriptions__label.el-descriptions__cell.is-bordered-label {
    width: 94px;
    color: #6f99aa;
    background: rgba(0, 229, 255, 0.045);
  }
  .el-descriptions__content.el-descriptions__cell.is-bordered-content {
    color: #c7e8ef;
    background: rgba(3, 16, 32, 0.45);
  }
}

.command-drawer-modal { background: rgba(0, 5, 12, 0.42) !important; }

.command-dispatch-dialog.el-dialog {
  color: #dffaff;
  border: 1px solid rgba(0, 229, 255, 0.22);
  background: rgba(3, 15, 31, 0.98);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.55), 0 0 25px rgba(0, 229, 255, 0.08);

  .el-dialog__title { color: #eafaff; }
  .el-form-item__label { color: #86aebd; }
  .el-input__wrapper,
  .el-select__wrapper,
  .el-textarea__inner {
    color: #dffaff;
    background: rgba(5, 25, 47, 0.88) !important;
    box-shadow: 0 0 0 1px rgba(0, 229, 255, 0.18) inset !important;
  }
  .el-input__inner,
  .el-textarea__inner,
  .el-select__selected-item { color: #dffaff !important; }
}

.drawer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.drawer-media {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
}

.drawer-image,
.drawer-video {
  width: 100%;
  height: 110px;
  border-radius: 8px;
  background: #000;
}

.drawer-algorithm-panel {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid rgba(0, 229, 255, 0.16);
  border-radius: 8px;
  background: rgba(0, 229, 255, 0.045);
}

.drawer-algorithm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;

  h4 {
    margin: 0;
    color: #eafaff;
    font-size: 14px;
    font-weight: 700;
  }
}

.drawer-algorithm-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;

  div {
    min-width: 0;
    padding: 8px;
    border-radius: 6px;
    background: rgba(3, 16, 32, 0.5);
  }

  span {
    display: block;
    margin-bottom: 4px;
    color: #6f99aa;
    font-size: 12px;
  }

  strong,
  code {
    color: #dffaff;
    font-size: 13px;
    font-weight: 700;
    overflow-wrap: anywhere;
  }

  code {
    font-family: ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace;
    font-weight: 500;
  }
}

.drawer-factor-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.drawer-evidence {
  margin: 10px 0 0;
  color: #a9ced8;
  font-size: 13px;
  line-height: 1.6;
}

.selected-vehicle-box {
  width: 100%;
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid rgba(80, 211, 255, 0.22);
  border-radius: 8px;
  background: rgba(3, 22, 40, 0.54);
}

.selected-vehicle-main {
  min-width: 0;
}

.selected-vehicle-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: #eafaff;
}

.selected-vehicle-meta,
.selected-vehicle-empty {
  margin-top: 5px;
  font-size: 12px;
  color: #8fb4c4;
}

.eta-dialog-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.eta-dialog-title {
  color: #15364d;
  font-size: 16px;
  font-weight: 700;
}

.eta-dialog-tip {
  margin-top: 5px;
  color: #6b8595;
  font-size: 13px;
}

.eta-rank {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #edf4f7;
  color: #5d7787;
  font-weight: 700;
}

.eta-rank.is-fastest {
  background: rgba(24, 190, 120, 0.14);
  color: #13a86d;
}

.eta-vehicle-cell {
  line-height: 1.45;
}

.eta-vehicle-name {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  color: #17384e;
  font-weight: 700;
}

.eta-vehicle-cell > span {
  color: #718a99;
  font-size: 12px;
}

.eta-time-value {
  color: #0b8fa5;
  font-size: 15px;
}

.eta-selected-summary {
  margin-top: 14px;
  padding: 11px 14px;
  border: 1px solid rgba(64, 158, 255, 0.22);
  border-radius: 7px;
  background: rgba(64, 158, 255, 0.08);
  color: #36576d;
}

.is-selected-eta-row > td.el-table__cell {
  background: rgba(64, 158, 255, 0.1) !important;
}

/* ====== YOLO 分析媒体弹窗 ====== */
.command-media-dialog.el-dialog {
  color: #dffaff;
  border: 1px solid rgba(0, 229, 255, 0.22);
  background: rgba(3, 15, 31, 0.98);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.55), 0 0 25px rgba(0, 229, 255, 0.08);

  .el-dialog__title { color: #eafaff; }
}

.media-dialog-body { min-height: 200px; }

.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.media-card {
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(0, 229, 255, 0.14);
  border-radius: 10px;
  overflow: hidden;
  background: rgba(5, 22, 43, 0.7);
  transition: border-color 0.2s;

  &:hover { border-color: rgba(0, 229, 255, 0.4); }
}

.media-thumb {
  width: 100%;
  height: 170px;
  cursor: pointer;

  .el-image__inner { object-fit: cover; }
}

.media-video {
  width: 100%;
  height: 170px;
  background: #000;
  outline: none;

  &.is-error {
    display: flex;
    align-items: center;
    justify-content: center;

    &::after {
      content: '⚠ 视频加载失败';
      color: #ef4444;
      font-size: 13px;
    }
  }
}

.media-placeholder {
  width: 100%;
  height: 170px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #4f7587;
  background: rgba(3, 16, 32, 0.6);

  span { font-size: 11px; }
}

.media-label {
  padding: 6px 10px 0;
}

.media-ai-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 10px 10px;
}

.media-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 0;
  color: #42697a;

  p { margin: 0; font-size: 13px; }
  span { font-size: 11px; color: #527486; }
}
</style>
