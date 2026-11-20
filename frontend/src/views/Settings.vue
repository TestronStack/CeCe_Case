<template>
  <div>
    <div class="page-head">
      <h2>设置</h2>
      <p>管理个人资料、登录密码与大模型配置</p>
    </div>

    <div class="settings-layout">
      <div class="panel side-tabs">
        <div v-for="tab in tabs" :key="tab.key" class="tab-item"
             :class="{ active: active === tab.key }" @click="active = tab.key">
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </div>
      </div>

      <div class="panel panel-pad content-card">
        <!-- 个人资料 -->
        <template v-if="active === 'profile'">
          <div class="panel-title"><h3>个人资料</h3></div>
          <div class="profile-head">
            <div class="avatar-lg">{{ initial }}</div>
            <div>
              <strong>{{ user.nickname || user.username }}</strong>
              <span>账号：{{ user.username }}</span>
            </div>
          </div>
          <el-form label-position="top" style="max-width: 420px">
            <el-form-item label="昵称">
              <el-input v-model="profile.nickname" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profile.email" placeholder="选填" />
            </el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
          </el-form>
        </template>

        <!-- 修改密码 -->
        <template v-if="active === 'password'">
          <div class="panel-title"><h3>修改密码</h3></div>
          <el-form label-position="top" style="max-width: 420px">
            <el-form-item label="原密码">
              <el-input v-model="pwd.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="pwd.confirm" type="password" show-password />
            </el-form-item>
            <el-button type="primary" :loading="savingPwd" @click="savePassword">更新密码</el-button>
          </el-form>
        </template>

        <!-- 模型设置 -->
        <template v-if="active === 'ai'">
          <div class="panel-title">
            <h3>模型配置</h3>
            <el-tag :type="ai.configured ? 'success' : 'info'" effect="light" size="small">
              {{ ai.configured ? '已配置 Key' : '未配置，使用服务端默认模型' }}
            </el-tag>
          </div>
          <el-form label-position="top" style="max-width: 520px">
            <el-form-item label="模型">
              <el-select v-model="aiForm.modelName" style="width: 100%" @change="onModelChange">
                <el-option v-for="m in models" :key="m.id" :label="m.name" :value="m.id">
                  <div class="model-option">
                    <span>{{ m.name }}</span>
                    <em>{{ m.desc }}</em>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="aiForm.apiKey" type="password" show-password
                        :placeholder="ai.apiKeyMasked || 'sk-...'" />
              <div class="field-hint">留空表示不修改已保存的 Key；Key 使用 AES 加密后存入数据库</div>
            </el-form-item>
            <el-button type="primary" :loading="savingAi" @click="saveAi">保存并生效</el-button>
            <div class="active-model">当前运行模型：{{ ai.activeModel || '未初始化' }}</div>
          </el-form>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const tabs = [
  { key: 'profile', label: '个人资料', icon: 'User' },
  { key: 'password', label: '修改密码', icon: 'Lock' },
  { key: 'ai', label: '模型配置', icon: 'Cpu' }
]

const active = ref('profile')
const user = ref(JSON.parse(localStorage.getItem('cece_user') || '{}'))
const profile = ref({ nickname: user.value.nickname || '', email: user.value.email || '' })
const pwd = ref({ oldPassword: '', newPassword: '', confirm: '' })
const ai = ref({})
const models = ref([])
const aiForm = ref({ modelName: 'deepseek-chat', provider: 'deepseek', apiKey: '' })
const savingProfile = ref(false)
const savingPwd = ref(false)
const savingAi = ref(false)

const initial = computed(() => (user.value.nickname || user.value.username || 'U').slice(0, 1).toUpperCase())

async function saveProfile() {
  savingProfile.value = true
  try {
    const res = await http.put('/auth/profile', profile.value)
    user.value = res.data.user
    localStorage.setItem('cece_user', JSON.stringify(res.data.user))
    ElMessage.success('资料已更新')
  } finally {
    savingProfile.value = false
  }
}

async function savePassword() {
  if (pwd.value.newPassword !== pwd.value.confirm) return ElMessage.warning('两次输入的新密码不一致')
  if (pwd.value.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  savingPwd.value = true
  try {
    await http.put('/auth/password', {
      oldPassword: pwd.value.oldPassword,
      newPassword: pwd.value.newPassword
    })
    ElMessage.success('密码已修改')
    pwd.value = { oldPassword: '', newPassword: '', confirm: '' }
  } finally {
    savingPwd.value = false
  }
}

function onModelChange(id) {
  const m = models.value.find((x) => x.id === id)
  if (m) aiForm.value.provider = m.provider
}

async function saveAi() {
  savingAi.value = true
  try {
    await http.put('/settings/ai', aiForm.value)
    ElMessage.success('模型配置已保存并生效')
    aiForm.value.apiKey = ''
    loadAi()
  } finally {
    savingAi.value = false
  }
}

async function loadAi() {
  const res = await http.get('/settings/ai')
  ai.value = res.data
  models.value = res.data.models || []
  aiForm.value.modelName = res.data.modelName || 'deepseek-chat'
  aiForm.value.provider = res.data.provider || 'deepseek'
}

onMounted(loadAi)
</script>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
  align-items: start;
}

.side-tabs {
  padding: 10px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border-radius: 10px;
  color: #4b5670;
  font-size: 13.5px;
  cursor: pointer;
  transition: all 0.16s ease;
}

.tab-item:hover {
  background: #f4f7fd;
}

.tab-item.active {
  background: var(--brand-light);
  color: var(--brand);
  font-weight: 600;
}

.content-card {
  min-height: 420px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 26px;
}

.avatar-lg {
  width: 62px;
  height: 62px;
  border-radius: 50%;
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 600;
}

.profile-head strong {
  display: block;
  font-size: 16px;
  margin-bottom: 4px;
}

.profile-head span {
  font-size: 12.5px;
  color: var(--text-3);
}

.field-hint {
  font-size: 11.5px;
  color: var(--text-3);
  margin-top: 6px;
  line-height: 1.5;
}

.active-model {
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-3);
}

.model-option {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.model-option em {
  font-style: normal;
  color: var(--text-3);
  font-size: 12px;
}

@media (max-width: 900px) {
  .settings-layout {
    grid-template-columns: 1fr;
  }
}
</style>
