<template>
  <div class="photo-uploader">
    <div class="upload-area">
      <!-- 文件上传 -->
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        list-type="picture-card"
        :file-list="fileList"
        :on-change="handleChange"
        :on-remove="handleRemove"
        :accept="accept"
        multiple
      >
        <el-icon :size="28"><Plus /></el-icon>
        <template #tip>
          <div class="upload-tip">点击或拖拽上传照片</div>
        </template>
      </el-upload>

      <!-- 拍照上传 -->
      <div class="camera-upload" v-if="showCamera">
        <el-button type="default" @click="handleCamera">
          <el-icon><Camera /></el-icon>
          拍照上传
        </el-button>
      </div>
    </div>

    <!-- 隐藏的拍照 input -->
    <input
      ref="cameraInput"
      type="file"
      accept="image/*"
      capture="environment"
      style="display: none"
      @change="handleCameraCapture"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Camera } from '@element-plus/icons-vue'
// 不导入 VueUpload 的 uploadRef 类型，用 ref 即可

const emit = defineEmits(['update:modelValue', 'change'])
const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  accept: { type: String, default: 'image/*' },
  showCamera: { type: Boolean, default: true },
  maxCount: { type: Number, default: 9 },
})

const uploadRef = ref(null)
const cameraInput = ref(null)
const fileList = ref([...props.modelValue])

function handleChange(uploadFile, uploadFiles) {
  const files = uploadFiles.map((f) => ({
    id: f.uid,
    name: f.name,
    url: f.url || URL.createObjectURL(f.raw),
    raw: f.raw,
  }))
  emitFiles(files)
}

function handleRemove(uploadFile, uploadFiles) {
  const files = uploadFiles.map((f) => ({
    id: f.uid,
    name: f.name,
    url: f.url,
    raw: f.raw,
  }))
  emitFiles(files)
}

function handleCamera() {
  cameraInput.value?.click()
}

function handleCameraCapture(e) {
  const file = e.target.files[0]
  if (!file) return
  if (fileList.value.length >= props.maxCount) {
    ElMessage.warning(`最多上传 ${props.maxCount} 张照片`)
    return
  }
  const newFile = {
    id: Date.now(),
    name: file.name,
    url: URL.createObjectURL(file),
    raw: file,
  }
  fileList.value.push(newFile)
  emitFiles(fileList.value)
  // 重置 input 以便重新选择同一文件
  e.target.value = ''
}

function emitFiles(files) {
  emit('update:modelValue', files)
  emit('change', files)
}

// 暴露方法供父组件调用
defineExpose({ fileList, clear: () => { fileList.value = [] } })
</script>

<style lang="scss" scoped>
.photo-uploader {
  .upload-area {
    display: flex;
    gap: 16px;
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .upload-tip {
    font-size: 12px;
    color: #9ca3af;
    margin-top: 4px;
  }

  .camera-upload {
    display: flex;
    align-items: center;
  }
}
</style>
