<template>
  <div>
    <div class="page-head flex-between">
      <div>
        <h2>文档库</h2>
        <p>你上传的需求文档与接口文档都会归档在这里，可重复用于生成</p>
      </div>
      <el-upload :show-file-list="false" :http-request="upload">
        <el-button type="primary"><el-icon><Upload /></el-icon>上传文档</el-button>
      </el-upload>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-select v-model="projectId" clearable placeholder="全部项目" style="width: 180px" @change="load">
          <el-option v-for="p in projectStore.projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <span class="muted">共 {{ documents.length }} 份文档</span>
      </div>

      <el-table :data="documents" v-loading="loading" empty-text="暂无文档，点击右上角上传">
        <el-table-column label="文档名称" min-width="280">
          <template #default="{ row }">
            <div class="name-cell">
              <div class="file-icon" :class="iconClass(row.ext)">
                {{ (row.ext || 'doc').toUpperCase().slice(0, 3) }}
              </div>
              <div class="file-meta">
                <strong>{{ row.originalName }}</strong>
                <span>{{ formatSize(row.size) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="项目" width="150">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="download(row)">下载</el-button>
            <el-button text @click="generate(row)">去生成</el-button>
            <el-popconfirm title="删除该文档？" @confirm="remove(row)">
              <template #reference>
                <el-button text type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http, { downloadBlob } from '../api'
import { useProjectStore } from '../stores/project'

const router = useRouter()
const projectStore = useProjectStore()
const documents = ref([])
const loading = ref(false)
const projectId = ref(projectStore.currentId)

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

function formatSize(size) {
  if (!size) return '0 KB'
  return size < 1024 * 1024 ? `${(size / 1024).toFixed(0)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`
}

function projectName(id) {
  return projectStore.projects.find((p) => p.id === id)?.name || '未归属'
}

function iconClass(ext) {
  return ['json', 'yaml', 'yml'].includes(ext) ? 'api' : 'doc'
}

async function load() {
  loading.value = true
  try {
    const res = await http.get('/documents', { params: { projectId: projectId.value || undefined } })
    documents.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function upload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  if (projectId.value) fd.append('projectId', projectId.value)
  await http.post('/documents', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  ElMessage.success('上传成功')
  load()
}

function download(row) {
  downloadBlob(`/api/documents/${row.id}/download`, row.originalName)
}

function generate(row) {
  const isApi = ['json', 'yaml', 'yml'].includes(row.ext)
  router.push(isApi ? '/generate/api' : '/generate/requirement')
}

async function remove(row) {
  await http.delete(`/documents/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.4px;
  flex-shrink: 0;
}

.file-icon.doc {
  background: #eff4ff;
  color: #2563eb;
}

.file-icon.api {
  background: #ecfdf5;
  color: #059669;
}

.file-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-meta strong {
  font-size: 13px;
  font-weight: 500;
}

.file-meta span {
  font-size: 11.5px;
  color: var(--text-3);
}
</style>
