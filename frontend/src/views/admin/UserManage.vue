<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>用户管理</h2>
      <p>管理系统用户账号、角色和权限</p>
    </div>

    <div class="page-card">
      <div class="toolbar">
        <el-button type="primary" @click="showDialog = true">
          <el-icon><Plus /></el-icon>
          新增用户
        </el-button>
        <div class="search-bar">
          <el-input v-model="searchKey" placeholder="搜索用户名/姓名" size="small" clearable style="width:200px" />
        </div>
      </div>

      <el-table :data="filteredUsers" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="姓名" width="100" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small" effect="plain">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="150" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editUser(row)">编辑</el-button>
            <el-button
              size="small"
              :type="row.status === '启用' ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === '启用' ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm title="确认删除该用户？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑用户弹窗 -->
    <el-dialog v-model="showDialog" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="opt in ROLE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="启用" inactive-value="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getUserList, updateUser, deleteUser } from '@/services/modules/user'
import { ROLE_OPTIONS } from '@/utils/role'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const users = ref([])
const searchKey = ref('')
const showDialog = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  username: '',
  nickname: '',
  password: '',
  role: '',
  status: '启用',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const filteredUsers = computed(() => {
  if (!searchKey.value) return users.value
  const key = searchKey.value.toLowerCase()
  return users.value.filter(
    (u) => u.username.toLowerCase().includes(key) || u.nickname.includes(key)
  )
})

function roleLabel(role) {
  const map = { POLICE: '现场交警', COMMAND: '指挥中心', RESCUE: '清障救援', ADMIN: '系统管理员' }
  return map[role] || role
}

function roleTagType(role) {
  const map = { POLICE: 'warning', COMMAND: 'danger', RESCUE: 'success', ADMIN: 'info' }
  return map[role] || 'info'
}

async function fetchUsers() {
  try {
    const res = await getUserList()
    if (res.code === 200) {
      users.value = res.data.list
    }
  } catch {}
}

function editUser(user) {
  isEdit.value = true
  form.id = user.id
  form.username = user.username
  form.nickname = user.nickname
  form.password = ''
  form.role = user.role
  form.status = user.status
  showDialog.value = true
}

function toggleStatus(user) {
  const newStatus = user.status === '启用' ? '禁用' : '启用'
  updateUser({ id: user.id, status: newStatus }).then((res) => {
    if (res.code === 200) {
      user.status = newStatus
      ElMessage.success(`用户已${newStatus}`)
    }
  })
}

async function handleDelete(id) {
  const res = await deleteUser({ id })
  if (res.code === 200) {
    ElMessage.success('用户已删除')
    fetchUsers()
  }
}

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const res = await updateUser({ ...form })
    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '用户更新成功' : '用户创建成功')
      showDialog.value = false
      fetchUsers()
    }
  } finally {
    saving.value = false
  }
}

onMounted(fetchUsers)
</script>

<style lang="scss" scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
