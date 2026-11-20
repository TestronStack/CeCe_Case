import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const http = axios.create({ baseURL: '/api', timeout: 300000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('cece_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '请求失败'
    if (status === 401) {
      localStorage.removeItem('cece_token')
      localStorage.removeItem('cece_user')
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login' })
      }
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http

export function tokenParam() {
  return encodeURIComponent(localStorage.getItem('cece_token') || '')
}

export async function downloadBlob(url, fallbackName) {
  const token = localStorage.getItem('cece_token')
  const res = await axios.get(url, {
    responseType: 'blob',
    headers: { Authorization: `Bearer ${token}` }
  })
  const disposition = res.headers['content-disposition'] || ''
  let name = fallbackName
  const utf = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf) name = decodeURIComponent(utf[1])
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = name
  link.click()
  URL.revokeObjectURL(link.href)
}
