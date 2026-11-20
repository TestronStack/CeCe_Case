<template>
  <div class="auth">
    <section class="hero">
      <div class="hero-inner">
        <div class="hero-brand">
          <div class="logo"><el-icon><MagicStick /></el-icon></div>
          <span>测测 CeCe</span>
        </div>
        <h1>让测试用例<br />从文档自动生长</h1>
        <p class="hero-sub">
          对接大模型，一条流水线完成文档解析、用例设计、格式标准化与自动化脚本生成。
        </p>
        <div class="hero-points">
          <div class="point">
            <el-icon><DocumentChecked /></el-icon>
            <div><strong>需求文档 → UI 用例</strong><span>按章节精准生成，15 列标准模板</span></div>
          </div>
          <div class="point">
            <el-icon><Connection /></el-icon>
            <div><strong>接口文档 → 接口用例</strong><span>自动识别接口，24 列覆盖断言与业务码</span></div>
          </div>
          <div class="point">
            <el-icon><Cpu /></el-icon>
            <div><strong>一键生成自动化工程</strong><span>Java / Python 脚本开箱即用</span></div>
          </div>
        </div>
        <div class="hero-foot">毕业设计项目 · AI 测试平台</div>
      </div>
      <div class="glow glow-a"></div>
      <div class="glow glow-b"></div>
    </section>

    <section class="form-side">
      <div class="form-card">
        <div class="form-head">
          <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <p>{{ mode === 'login' ? '登录后继续你的测试任务' : '注册后数据将自动归档到你的账号' }}</p>
        </div>

        <el-tabs v-model="mode" class="mode-tabs">
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @submit.prevent>
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入账号" prefix-icon="User" />
          </el-form-item>
          <el-form-item v-if="mode === 'register'" label="昵称" prop="nickname">
            <el-input v-model="form.nickname" size="large" placeholder="选填，默认同账号" prefix-icon="Avatar" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" size="large" show-password
                      placeholder="至少 6 位" prefix-icon="Lock" @keyup.enter="submit" />
          </el-form-item>

          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            {{ mode === 'login' ? '登 录' : '注册并登录' }}
          </el-button>
        </el-form>

        <div class="tip">
          <el-icon><InfoFilled /></el-icon>
          演示账号：admin / 123456
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const loading = ref(false)
const formRef = ref()
const form = ref({ username: '', password: '', nickname: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }, { min: 3, message: '账号至少 3 个字符', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, message: '密码至少 6 位', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const url = mode.value === 'login' ? '/auth/login' : '/auth/register'
    const res = await http.post(url, form.value)
    localStorage.setItem('cece_token', res.data.token)
    localStorage.setItem('cece_user', JSON.stringify(res.data.user))
    ElMessage.success(res.message || '登录成功')
    router.push(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  display: flex;
  min-height: 100vh;
  background: #fff;
}

.hero {
  flex: 1.1;
  position: relative;
  overflow: hidden;
  background: linear-gradient(150deg, #1e3a8a 0%, #2563eb 45%, #3b82f6 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 64px;
}

.hero-inner {
  position: relative;
  z-index: 2;
  max-width: 460px;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.5px;
  margin-bottom: 56px;
}

.hero-brand .logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
}

.hero h1 {
  font-size: 40px;
  line-height: 1.28;
  margin: 0 0 18px;
  font-weight: 650;
  letter-spacing: 0.5px;
}

.hero-sub {
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  line-height: 1.8;
  margin: 0 0 40px;
}

.hero-points {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.point {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(6px);
}

.point .el-icon {
  font-size: 20px;
  margin-top: 2px;
}

.point strong {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  margin-bottom: 3px;
}

.point span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.72);
}

.hero-foot {
  margin-top: 46px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  letter-spacing: 1px;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.5;
}

.glow-a {
  width: 380px;
  height: 380px;
  right: -90px;
  top: -80px;
  background: #60a5fa;
}

.glow-b {
  width: 320px;
  height: 320px;
  right: 60px;
  bottom: -110px;
  background: #1e40af;
}

.form-side {
  flex: 0.9;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: #fff;
}

.form-card {
  width: 100%;
  max-width: 380px;
}

.form-head h2 {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 650;
}

.form-head p {
  margin: 0 0 26px;
  color: var(--text-2);
  font-size: 13px;
}

.mode-tabs {
  margin-bottom: 8px;
}

.submit {
  width: 100%;
  margin-top: 6px;
  height: 44px;
  font-size: 15px;
  letter-spacing: 2px;
}

.tip {
  margin-top: 22px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 9px;
  background: var(--brand-light);
  color: #3b5aa8;
  font-size: 12px;
}

@media (max-width: 900px) {
  .hero {
    display: none;
  }
}
</style>
