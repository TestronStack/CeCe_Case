<template>
  <div>
    <div class="page-head">
      <h2>自然语言生成</h2>
      <p>用一段话描述接口或需求，AI 直接产出结构化测试用例</p>
    </div>

    <div class="two-col">
      <div class="panel panel-pad">
        <div class="panel-title"><h3>1. 输入描述</h3></div>
        <el-form label-position="top">
          <el-form-item label="描述类型">
            <el-radio-group v-model="type">
              <el-radio-button value="interface">接口描述</el-radio-button>
              <el-radio-button value="requirement">需求描述</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="内容">
            <el-input v-model="content" type="textarea" :rows="10" maxlength="3000" show-word-limit
                      :placeholder="placeholder" />
          </el-form-item>

          <el-form-item label="生成数量">
            <el-slider v-model="generateCount" :min="1" :max="50" show-input />
          </el-form-item>
        </el-form>
      </div>

      <div class="panel panel-pad">
        <div class="panel-title"><h3>2. 输出设置</h3></div>
        <el-form label-position="top">
          <el-form-item label="归属项目">
            <el-select v-model="projectId" clearable placeholder="不归属项目" style="width: 100%">
              <el-option v-for="p in projectStore.projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="创建人">
            <el-input v-model="creator" />
          </el-form-item>
          <el-form-item label="导出格式">
            <el-select v-model="outputFormat" style="width: 100%">
              <el-option label="Excel (.xlsx)" value="xlsx" />
              <el-option label="CSV (.csv)" value="csv" />
              <el-option label="JSON (.json)" value="json" />
              <el-option label="Markdown (.md)" value="md" />
            </el-select>
          </el-form-item>
          <el-form-item label="自定义列（可选）">
            <el-input v-model="customTemplate" type="textarea" :rows="3"
                      placeholder="用 | 分隔列名，留空使用标准模板" />
          </el-form-item>
          <el-button type="primary" size="large" class="gen-btn" :loading="submitting"
                     :disabled="content.trim().length < 20" @click="submit">
            <el-icon><ChatDotRound /></el-icon>生成测试用例
          </el-button>
        </el-form>
      </div>
    </div>

    <div v-if="task" class="panel panel-pad mt-20">
      <div class="panel-title"><h3>生成进度</h3><span class="sub">实时推送</span></div>
      <TaskProgress :task="task" @update="onUpdate" @view="viewCases" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'
import TaskProgress from '../components/TaskProgress.vue'
import { useProjectStore } from '../stores/project'

const router = useRouter()
const projectStore = useProjectStore()

const type = ref('interface')
const content = ref('')
const generateCount = ref(10)
const projectId = ref(projectStore.currentId)
const creator = ref('')
const outputFormat = ref('xlsx')
const customTemplate = ref('')
const submitting = ref(false)
const task = ref(null)

const placeholder = computed(() => type.value === 'interface'
  ? '示例：用户登录接口 POST /api/login，入参 username（必填，4-20 位）与 password（必填，6-32 位，需含字母和数字），成功返回 200 与 token，失败返回 4001 账号不存在 / 4002 密码错误 / 4003 账号锁定。'
  : '示例：用户可以在个人中心修改昵称，昵称长度 2-20 个字符，不允许包含特殊符号，保存成功后页面即时刷新并提示，重复昵称需提示已被占用。')

onMounted(() => {
  const user = JSON.parse(localStorage.getItem('cece_user') || '{}')
  creator.value = user.nickname || user.username || ''
})

async function submit() {
  submitting.value = true
  try {
    const res = await http.post('/generation/text', {
      content: content.value,
      type: type.value,
      generateCount: generateCount.value,
      projectId: projectId.value || '',
      creator: creator.value,
      outputFormat: outputFormat.value,
      customTemplate: customTemplate.value
    })
    task.value = res.data
    ElMessage.success('任务已提交')
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
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}

.gen-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
</style>
