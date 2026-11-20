<template>
  <div>
    <div class="page-head flex-between">
      <div>
        <h2>用例库</h2>
        <p>每次生成都会形成独立批次，支持查看、编辑、评审与再次导出</p>
      </div>
      <el-button type="primary" @click="router.push('/generate/requirement')">
        <el-icon><Plus /></el-icon>新建生成
      </el-button>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索批次名称" clearable prefix-icon="Search"
                  style="width: 240px" @input="debouncedLoad" />
        <el-select v-model="projectId" clearable placeholder="全部项目" style="width: 180px" @change="load">
          <el-option v-for="p in projectStore.projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <span class="muted">共 {{ batches.length }} 个批次</span>
      </div>

      <el-table :data="batches" v-loading="loading" empty-text="还没有生成记录，去创建一个任务吧">
        <el-table-column prop="name" label="批次名称" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="name-cell">
              <span class="dot" :style="{ background: row.caseType === 'API' ? '#059669' : '#2563eb' }"></span>
              <span class="name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="caseType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.caseType === 'API' ? 'success' : 'primary'" effect="light">
              {{ row.caseType === 'API' ? '接口用例' : 'UI 用例' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="caseCount" label="用例数" width="90" align="center" />
        <el-table-column prop="outputFormat" label="格式" width="90">
          <template #default="{ row }">{{ row.outputFormat?.toUpperCase() }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="router.push(`/cases/${row.id}`)">查看</el-button>
            <el-button text @click="download(row)">下载</el-button>
            <el-dropdown @command="(f) => exportAs(row, f)">
              <el-button text>导出<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="xlsx">Excel</el-dropdown-item>
                  <el-dropdown-item command="csv">CSV</el-dropdown-item>
                  <el-dropdown-item command="json">JSON</el-dropdown-item>
                  <el-dropdown-item command="md">Markdown</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-popconfirm title="删除该批次及全部用例？" confirm-button-text="删除"
                           cancel-button-text="取消" @confirm="remove(row)">
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
const batches = ref([])
const loading = ref(false)
const keyword = ref('')
const projectId = ref(projectStore.currentId)
let timer = null

function formatTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

async function load() {
  loading.value = true
  try {
    const res = await http.get('/batches', {
      params: { projectId: projectId.value || undefined, keyword: keyword.value || undefined }
    })
    batches.value = res.data || []
  } finally {
    loading.value = false
  }
}

function debouncedLoad() {
  clearTimeout(timer)
  timer = setTimeout(load, 300)
}

function download(row) {
  downloadBlob(`/api/batches/${row.id}/download`, row.name + '.' + row.outputFormat)
}

function exportAs(row, format) {
  downloadBlob(`/api/batches/${row.id}/export?format=${format}`, row.name + '.' + format)
}

async function remove(row) {
  await http.delete(`/batches/${row.id}`)
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
  gap: 9px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.name {
  font-weight: 500;
  color: var(--text);
}
</style>
