<template>
  <div class="doc-source">
    <el-form-item label="归属项目">
      <el-select :model-value="projectId" placeholder="不归属项目" clearable style="width: 100%"
                 @update:model-value="$emit('update:projectId', $event)">
        <el-option v-for="p in projectStore.projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
    </el-form-item>

    <el-form-item label="文档来源">
      <div class="source-tabs">
        <el-radio-group :model-value="source" @update:model-value="changeSource">
          <el-radio-button value="upload">上传新文档</el-radio-button>
          <el-radio-button value="library">从文档库选择</el-radio-button>
        </el-radio-group>
      </div>
    </el-form-item>

    <el-form-item v-if="source === 'upload'">
      <el-upload drag :show-file-list="false" :http-request="upload" :accept="accept" class="uploader">
        <el-icon class="up-icon"><UploadFilled /></el-icon>
        <div class="up-text">
          <strong>{{ fileName || '点击或拖拽上传文档' }}</strong>
          <span>{{ acceptText }}</span>
        </div>
      </el-upload>
      <div v-if="uploading" class="uploading">
        <el-progress :percentage="100" :indeterminate="true" :show-text="false" />
        <span>正在上传…</span>
      </div>
    </el-form-item>

    <el-form-item v-else>
      <el-select :model-value="modelValue" filterable placeholder="选择已上传的文档"
                 style="width: 100%" @update:model-value="pick">
        <el-option v-for="d in docs" :key="d.id" :label="d.originalName" :value="d.id">
          <div class="doc-option">
            <span>{{ d.originalName }}</span>
            <em>{{ formatSize(d.size) }}</em>
          </div>
        </el-option>
      </el-select>
    </el-form-item>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { useProjectStore } from '../stores/project'

const props = defineProps({
  modelValue: { type: Number, default: null },
  projectId: { type: Number, default: null },
  accept: { type: String, default: '.docx,.pdf,.md,.txt,.json,.yaml,.yml' },
  acceptText: { type: String, default: '支持 DOCX / PDF / MD / TXT / JSON / YAML' }
})
const emit = defineEmits(['update:modelValue', 'update:projectId', 'uploaded'])

const projectStore = useProjectStore()
const source = ref('upload')
const docs = ref([])
const uploading = ref(false)
const fileName = ref('')

const sizeText = computed(() => (props.accept.includes('json') ? '接口文档' : '需求文档'))

function changeSource(val) {
  source.value = val
  if (val === 'library') loadDocs()
}

async function loadDocs() {
  const res = await http.get('/documents', { params: { projectId: props.projectId || undefined } })
  docs.value = res.data || []
}

async function upload({ file }) {
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    if (props.projectId) fd.append('projectId', props.projectId)
    const res = await http.post('/documents', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    fileName.value = res.data.originalName
    emit('update:modelValue', res.data.id)
    emit('uploaded', res.data)
    ElMessage.success('文档已上传并归档')
  } finally {
    uploading.value = false
  }
}

function pick(val) {
  emit('update:modelValue', val)
  const doc = docs.value.find((d) => d.id === val)
  if (doc) emit('uploaded', doc)
}

function formatSize(size) {
  if (!size) return '0 KB'
  if (size < 1024 * 1024) return (size / 1024).toFixed(0) + ' KB'
  return (size / 1024 / 1024).toFixed(1) + ' MB'
}

watch(() => props.projectId, () => {
  if (source.value === 'library') loadDocs()
})
</script>

<style scoped>
.source-tabs {
  margin-bottom: 4px;
}

.uploader {
  width: 100%;
}

:deep(.uploader .el-upload) {
  width: 100%;
}

:deep(.uploader .el-upload-dragger) {
  width: 100%;
  padding: 26px 16px;
  border-radius: 12px;
  border: 1px dashed #cfdaf0;
  background: #fafcff;
  transition: all 0.16s ease;
}

:deep(.uploader .el-upload-dragger:hover) {
  border-color: var(--brand);
  background: var(--brand-light);
}

.up-icon {
  font-size: 30px;
  color: var(--brand);
}

.up-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
}

.up-text strong {
  font-size: 13.5px;
  color: var(--text);
}

.up-text span {
  font-size: 12px;
  color: var(--text-3);
}

.uploading {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: var(--text-3);
}

.uploading .el-progress {
  flex: 1;
}

.doc-option {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.doc-option em {
  font-style: normal;
  color: var(--text-3);
  font-size: 12px;
}
</style>
