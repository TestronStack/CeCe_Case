<template>
  <div>
    <div class="page-head">
      <h2>自动化脚本生成</h2>
      <p>基于接口用例生成可直接运行的自动化测试工程（Java / Python，含用例 Excel）</p>
    </div>

    <div class="two-col">
      <div class="panel panel-pad">
        <div class="panel-title"><h3>1. 选择接口文档</h3></div>
        <el-form label-position="top">
          <DocSource v-model="documentId" v-model:projectId="projectId"
                     accept=".docx,.pdf,.json,.yaml,.yml,.md"
                     accept-text="支持 DOCX / PDF / JSON / YAML / MD" />

          <el-form-item label="接口基础地址">
            <el-input v-model="baseUrl" placeholder="http://localhost:8080" />
          </el-form-item>

          <el-form-item label="脚本语言">
            <el-radio-group v-model="language">
              <el-radio-button value="java">Java · TestNG + RestAssured</el-radio-button>
              <el-radio-button value="python">Python · pytest + requests</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="读取范围">
            <el-radio-group v-model="readRangeType">
              <el-radio-button value="all">全部读取</el-radio-button>
              <el-radio-button value="page">指定页数</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="readRangeType === 'page'" label="页码范围">
            <div class="flex gap-8" style="width: 100%">
              <el-input-number v-model="startPage" :min="1" :max="999" controls-position="right" />
              <span class="muted" style="line-height: 32px">至</span>
              <el-input-number v-model="endPage" :min="1" :max="999" controls-position="right" />
            </div>
          </el-form-item>
        </el-form>
      </div>

      <div class="panel panel-pad">
        <div class="panel-title"><h3>2. 工程内容</h3></div>
        <div class="pack">
          <div class="pack-item">
            <el-icon><Folder /></el-icon>
            <div><strong>pom.xml / requirements.txt</strong><span>依赖与构建配置</span></div>
          </div>
          <div class="pack-item">
            <el-icon><Document /></el-icon>
            <div><strong>接口测试用例.xlsx</strong><span>本次生成的 24 列用例</span></div>
          </div>
          <div class="pack-item">
            <el-icon><Cpu /></el-icon>
            <div><strong>测试类 / test 文件</strong><span>按接口分组，含断言与 Allure 注解</span></div>
          </div>
          <div class="pack-item">
            <el-icon><Reading /></el-icon>
            <div><strong>README</strong><span>运行命令与目录说明</span></div>
          </div>
        </div>
        <el-button type="primary" size="large" class="gen-btn" :loading="submitting"
                   :disabled="!documentId" @click="submit">
          <el-icon><MagicStick /></el-icon>生成自动化工程
        </el-button>
      </div>
    </div>

    <div v-if="task" class="panel panel-pad mt-20">
      <div class="panel-title"><h3>生成进度</h3><span class="sub">实时推送</span></div>
      <TaskProgress :task="task" @update="onUpdate" @view="viewCases" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'
import DocSource from '../components/DocSource.vue'
import TaskProgress from '../components/TaskProgress.vue'
import { useProjectStore } from '../stores/project'

const router = useRouter()
const projectStore = useProjectStore()

const documentId = ref(null)
const projectId = ref(projectStore.currentId)
const baseUrl = ref('http://localhost:8080')
const language = ref('java')
const readRangeType = ref('all')
const startPage = ref(1)
const endPage = ref(20)
const submitting = ref(false)
const task = ref(null)

async function submit() {
  if (!documentId.value) return ElMessage.warning('请先上传或选择文档')
  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('documentId', documentId.value)
    if (projectId.value) fd.append('projectId', projectId.value)
    fd.append('baseUrl', baseUrl.value)
    fd.append('language', language.value)
    fd.append('readRangeType', readRangeType.value)
    fd.append('startPage', startPage.value)
    fd.append('endPage', endPage.value)
    const res = await http.post('/generation/script', fd)
    task.value = res.data
    ElMessage.success('任务已提交，正在生成工程')
  } finally {
    submitting.value = false
  }
}

function onUpdate(data) {
  task.value = { ...task.value, ...data }
}

function viewCases(t) {
  router.push(`/cases/${t.batchId}`)
}
</script>

<style scoped>
.two-col {
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 16px;
}

.gen-btn {
  width: 100%;
  margin-top: 16px;
  height: 44px;
}

.pack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pack-item {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f7f9fd;
  border: 1px solid #eef2f9;
}

.pack-item .el-icon {
  font-size: 18px;
  color: var(--brand);
  margin-top: 2px;
}

.pack-item strong {
  display: block;
  font-size: 13px;
  margin-bottom: 2px;
}

.pack-item span {
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
