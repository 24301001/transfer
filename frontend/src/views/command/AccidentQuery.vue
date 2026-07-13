<template>
  <div class="accident-query-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>事故详情查询</h2>
    </div>

    <!-- 搜索筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filters" label-width="0" size="default">
        <el-row :gutter="16">
          <el-col :span="7">
            <el-form-item>
              <el-input
                v-model="filters.keyword"
                placeholder="搜索事故编号、地点、描述..."
                clearable
                @keyup.enter="handleSearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item>
              <el-select v-model="filters.status" placeholder="事故状态" clearable style="width:100%">
                <el-option label="全部状态" value="" />
                <el-option label="待处理" value="待处理" />
                <el-option label="处理中" value="处理中" />
                <el-option label="已处理" value="已处理" />
                <el-option label="已结案" value="已结案" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item>
              <el-select v-model="filters.riskLevel" placeholder="风险等级" clearable style="width:100%">
                <el-option label="全部等级" value="" />
                <el-option label="低" value="低" />
                <el-option label="中" value="中" />
                <el-option label="高" value="高" />
                <el-option label="严重" value="严重" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="7" style="display:flex; gap:8px;">
            <el-form-item>
              <el-button type="primary" @click="handleSearch" :loading="loading">
                <el-icon><Search /></el-icon> 查询
              </el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 结果统计 -->
    <div class="result-bar">
      <span class="result-count">
        共查询到 <strong>{{ total }}</strong> 条事故记录
      </span>
    </div>

    <!-- 卡片列表 -->
    <div class="query-list" v-loading="loading">
      <div v-if="list.length > 0" class="card-grid">
        <div
          v-for="item in list"
          :key="item.id"
          class="query-card"
          @click="openDrawer(item.id)"
        >
          <!-- 顶部：事故编号 + 风险等级 -->
          <div class="card-top">
            <span class="card-case-no">{{ item.caseNo }}</span>
            <RiskBadge :level="item.riskLevel" size="small" />
          </div>
          <!-- 纵向字段 -->
          <div class="card-fields">
            <div class="card-field">
              <span class="field-label">时&#8195;间</span>
              <span class="field-value">{{ item.reportTime }}</span>
            </div>
            <div class="card-field">
              <span class="field-label">地&#8195;点</span>
              <span class="field-value location">{{ item.location?.name || '-' }}</span>
            </div>
            <div class="card-field">
              <span class="field-label">类&#8195;型</span>
              <span class="field-value">{{ item.type }}</span>
            </div>
            <div class="card-field">
              <span class="field-label">状&#8195;态</span>
              <el-tag :type="statusType(item.status)" size="small" effect="plain">
                {{ item.status }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无匹配的事故记录" />
    </div>

    <!-- 分页 -->
    <div class="query-pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 事故详情侧边栏 -->
    <el-drawer
      v-model="drawerVisible"
      title="事故详情"
      :size="480"
      direction="rtl"
    >
      <template v-if="detailLoading">
        <div style="text-align:center; padding:40px 0;">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p style="margin-top:12px; color:var(--el-text-color-secondary);">加载中...</p>
        </div>
      </template>
      <template v-else-if="detail">
        <!-- 基本信息 -->
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="事故编号">{{ detail.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="事故类型">{{ detail.type }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <RiskBadge :level="detail.riskLevel" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ detail.riskScore }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small" effect="plain">
              {{ detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="地点">{{ detail.location?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上报时间">{{ detail.reportTime }}</el-descriptions-item>
          <el-descriptions-item label="预计拥堵">{{ detail.congestionDuration }}</el-descriptions-item>
          <el-descriptions-item label="恢复时间">{{ detail.recoveryTime }}</el-descriptions-item>
          <el-descriptions-item label="影响车道">{{ detail.affectedLanes }}</el-descriptions-item>
          <el-descriptions-item label="天气">{{ detail.weather }}</el-descriptions-item>
          <el-descriptions-item label="道路等级">{{ detail.roadLevel }}</el-descriptions-item>
          <el-descriptions-item label="处置建议">
            <el-alert
              v-if="detail.disposalAdvice"
              :title="detail.disposalAdvice"
              type="info"
              :closable="false"
              show-icon
            />
            <span v-else class="text-muted">暂无建议</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 附件图片 -->
        <template v-if="detail.images && detail.images.length > 0">
          <h4 class="section-title">附件 ({{ detail.images.length }})</h4>
          <div class="image-list">
            <el-image
              v-for="img in detail.images"
              :key="img.id"
              :src="img.url"
              :preview-src-list="detail.images.map(i => i.url)"
              fit="cover"
              class="preview-image"
            />
          </div>
        </template>

        <!-- 历史预测结果 -->
        <template v-if="detail.aiExplanation">
          <h4 class="section-title">AI 分析</h4>
          <el-alert
            :title="detail.aiExplanation"
            type="info"
            :closable="false"
            show-icon
          />
        </template>
      </template>
      <template v-else>
        <el-empty description="未找到事故详情" />
      </template>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAccidentList, getAccidentDetail } from '@/services/modules/accident'
import RiskBadge from '@/components/RiskBadge.vue'
import { Search, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

// ====== 筛选条件 ======
const filters = ref({
  keyword: '',
  status: '',
  riskLevel: '',
})

// ====== 列表数据 ======
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)

// ====== 查询 ======
async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize }
    if (filters.value.keyword) params.keyword = filters.value.keyword
    if (filters.value.status) params.status = filters.value.status
    if (filters.value.riskLevel) params.riskLevel = filters.value.riskLevel

    const res = await getAccidentList(params)
    if (res.code === 200) {
      list.value = res.data.list
      total.value = res.data.total
    }
  } catch (err) {
    ElMessage.error('查询失败: ' + (err.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  filters.value = { keyword: '', status: '', riskLevel: '' }
  page.value = 1
  fetchData()
}

function handlePageChange(p) {
  page.value = p
  fetchData()
}

// ====== 详情抽屉 ======
const drawerVisible = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

async function openDrawer(accidentId) {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getAccidentDetail(accidentId)
    if (res.code === 200) {
      detail.value = res.data
    }
  } catch (err) {
    ElMessage.error('加载详情失败: ' + (err.message || '未知错误'))
  } finally {
    detailLoading.value = false
  }
}

function statusType(status) {
  const map = { 待处理: 'danger', 处理中: 'warning', 已处理: 'success', 已结案: 'info' }
  return map[status] || 'info'
}

// ====== 初始化 ======
onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.accident-query-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-header {
  margin-bottom: 20px;

  h2 {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
  }
}

// ===== 筛选栏 =====
.filter-card {
  flex-shrink: 0;
  margin-bottom: 16px;
  border-radius: 12px;

  :deep(.el-card__body) {
    padding-bottom: 0;
  }
}

// ===== 结果统计 =====
.result-bar {
  flex-shrink: 0;
  margin-bottom: 14px;
  font-size: 13px;
  color: $text-secondary;

  strong {
    color: $accent;
    font-weight: 600;
  }
}

// ===== 卡片列表 =====
.query-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
  align-content: start;
  padding-bottom: 16px;
}

.query-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: $accent;
    box-shadow: 0 2px 12px rgba($accent, 0.10);
    transform: translateY(-1px);
  }

  // 顶部：编号 + 风险等级
  .card-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 14px;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .card-case-no {
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;
    font-family: $font-mono;
  }

  // 纵向字段
  .card-fields {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .card-field {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;

    .field-label {
      font-size: 12px;
      color: $text-light;
      flex-shrink: 0;
      width: 3em;
      text-align: right;
    }

    .field-value {
      font-size: 13px;
      color: $text-primary;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      &.location {
        max-width: 220px;
      }
    }
  }
}

// ===== 分页 =====
.query-pagination {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  padding: 16px 0 0;
  border-top: 1px solid var(--el-border-color-lighter);
  margin-top: 4px;
}

// ===== 详情侧栏内 =====
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin: 20px 0 10px;
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-image {
  width: 100px;
  height: 75px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: scale(1.05);
  }
}

.text-muted {
  color: $text-light;
  font-size: 13px;
}
</style>
