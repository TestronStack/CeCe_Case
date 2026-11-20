<template>
  <div v-if="batch">
    <div class="page-head flex-between">
      <div class="flex gap-12" style="align-items: flex-start">
        <el-button circle @click="router.push('/cases')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div>
          <h2>{{ batch.name }}</h2>
          <p>
            {{ batch.caseType === 'API' ? '接口用例' : 'UI 用例' }} ·
            共 {{ total }} 条 ·
            <span style="color:#059669">已通过 {{ approved }}</span> ·
            <span style="color:#b45309">待评审 {{ pending }}</span>
          </p>
        </div>
      </div>
      <div class="flex gap-8">
        <el-button @click="downloadFile">
          <el-icon><Download /></el-icon>下载原文件
        </el-button>
        <el-dropdown @command="exportAs">
          <el-button type="primary">
            导出当前内容<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="xlsx">Excel</el-dropdown-item>
              <el-dropdown-item command="csv">CSV</el-dropdown-item>
              <el-dropdown-item command="json">JSON</el-dropdown-item>
              <el-dropdown-item command="md">Markdown</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索编号或标题" clearable prefix-icon="Search"
                  style="width: 220px" />
        <el-select v-model="priority" clearable placeholder="优先级" style="width: 130px">
          <el-option v-for="p in ['P0', 'P1', 'P2', 'P3']" :key="p" :label="p" :value="p" />
        </el-select>
        <el-select v-model="reviewStatus" clearable placeholder="评审状态" style="width: 140px">
          <el-option label="待评审" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <span class="muted">共 {{ filtered.length }} 条</span>
      </div>

      <el-table :data="filtered" v-loading="loading" border height="calc(100vh - 330px)"
                size="small" :row-key="(r) => r.id">
        <el-table-column v-for="col in columns" :key="col.fieldName" :label="col.displayName"
                         :min-width="colWidth(col.fieldName)" :fixed="col.fieldName === 'id' ? 'left' : false">
          <template #default="{ row }">
            <el-tag v-if="col.fieldName === 'priority'" size="small"
                    :type="priorityType(row.data.priority)" effect="light">
              {{ row.data.priority || '-' }}
            </el-tag>
            <span v-else class="cell-text">{{ row.data[col.fieldName] || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="评审" width="96" fixed="right" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="reviewType(row.reviewStatus)" effect="light">
              {{ reviewText(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="row-ops">
              <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button text type="success" size="small" @click="review(row, 'APPROVED')">通过</el-button>
              <el-button text type="warning" size="small" @click="review(row, 'REJECTED')">驳回</el-button>
              <el-popconfirm title="删除该用例？" width="200" @confirm="remove(row)">
                <template #reference>
                  <el-button text type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="编辑用例" width="760px" top="6vh">
      <el-form v-if="editing" label-position="top" class="edit-form">
        <el-form-item v-for="col in columns" :key="col.fieldName" :label="col.displayName">
          <el-input v-if="isLong(col.fieldName)" v-model="editing.data[col.fieldName]"
                    type="textarea" :rows="3" />
          <el-input v-else v-model="editing.data[col.fieldName]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http, { downloadBlob } from '../api'

const route = useRoute()
const router = useRouter()
const batch = ref(null)
const columns = ref([])
const cases = ref([])
const loading = ref(false)
const dialog = ref(false)
const editing = ref(null)
const saving = ref(false)
const keyword = ref('')
const priority = ref('')
const reviewStatus = ref('')

const total = computed(() => cases.value.length)
const approved = computed(() => cases.value.filter((c) => c.reviewStatus === 'APPROVED').length)
const pending = computed(() => cases.value.filter((c) => c.reviewStatus === 'PENDING').length)

const filtered = computed(() => cases.value.filter((c) => {
  if (priority.value && c.data.priority !== priority.value) return false
  if (reviewStatus.value && c.reviewStatus !== reviewStatus.value) return false
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    return (c.title || '').toLowerCase().includes(k) || (c.caseNo || '').toLowerCase().includes(k)
  }
  return true
}))

function colWidth(field) {
  if (field === 'id') return 110
  if (['title'].includes(field)) return 240
  if (['steps', 'expectedResult', 'precondition', 'presetData', 'expectedResponseJson', 'requestParams'].includes(field)) return 260
  if (['priority', 'status'].includes(field)) return 90
  return 150
}

function isLong(field) {
  return ['steps', 'expectedResult', 'precondition', 'presetData', 'expectedResponseJson',
    'requestParams', 'requestHeaders', 'assertionRules', 'title'].includes(field)
}

function priorityType(p) {
  return { P0: 'danger', P1: 'warning', P2: 'primary', P3: 'info' }[p] || 'info'
}

function reviewType(s) {
  return { APPROVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

function reviewText(s) {
  return { APPROVED: '已通过', REJECTED: '已驳回' }[s] || '待评审'
}

async function load() {
  loading.value = true
  try {
    const res = await http.get(`/batches/${route.params.id}`)
    batch.value = res.data.batch
    columns.value = res.data.columns || []
    cases.value = (res.data.cases || []).map((c) => ({
      ...c,
      data: JSON.parse(c.dataJson || '{}')
    }))
  } finally {
    loading.value = false
  }
}

function openEdit(row) {
  editing.value = { ...row, data: { ...row.data } }
  dialog.value = true
}

async function save() {
  saving.value = true
  try {
    const res = await http.put(`/cases/${editing.value.id}`, { data: editing.value.data })
    const idx = cases.value.findIndex((c) => c.id === editing.value.id)
    if (idx >= 0) {
      cases.value[idx] = { ...res.data, data: JSON.parse(res.data.dataJson || '{}') }
    }
    dialog.value = false
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

async function review(row, status) {
  let remark = ''
  if (status === 'REJECTED') {
    try {
      const r = await ElMessageBox.prompt('请填写驳回原因', '驳回用例', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：预期结果描述不清晰'
      })
      remark = r.value || ''
    } catch (e) {
      return
    }
  }
  const res = await http.put(`/cases/${row.id}`, { reviewStatus: status, reviewRemark: remark })
  const idx = cases.value.findIndex((c) => c.id === row.id)
  if (idx >= 0) cases.value[idx] = { ...res.data, data: JSON.parse(res.data.dataJson || '{}') }
  ElMessage.success(status === 'APPROVED' ? '已通过' : '已驳回')
}

async function remove(row) {
  await http.delete(`/cases/${row.id}`)
  cases.value = cases.value.filter((c) => c.id !== row.id)
  ElMessage.success('已删除')
}

function downloadFile() {
  downloadBlob(`/api/batches/${batch.value.id}/download`, batch.value.name + '.' + batch.value.outputFormat)
}

function exportAs(format) {
  downloadBlob(`/api/batches/${batch.value.id}/export?format=${format}`, batch.value.name + '.' + format)
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.cell-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}

.row-ops {
  white-space: nowrap;
}

.row-ops :deep(.el-button) {
  padding: 4px 5px;
  font-size: 12.5px;
}

.row-ops :deep(.el-button + .el-button) {
  margin-left: 2px;
}

.edit-form {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 6px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.edit-form :deep(.el-form-item:has(textarea)) {
  grid-column: 1 / -1;
}
</style>
