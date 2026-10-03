import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const http = axios.create({ baseURL: '/', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.warning('请重新登录')
        router.push('/login')
      }
    } else {
      ElMessage.error(err.response?.data?.message || err.message || '网络错误')
    }
    return Promise.reject(err)
  }
)

// ---------- 认证 ----------
export const apiLogin = (data) => http.post('/api/auth/login', data)
export const apiRegister = (data) => http.post('/api/auth/register', data)
export const apiMe = () => http.get('/api/auth/me')

// ---------- 元数据 ----------
export const apiMeta = () => http.get('/api/meta')

// ---------- 事件 ----------
export const apiCreateIncident = (data) => http.post('/api/incidents', data)
export const apiIncidents = () => http.get('/api/incidents')
export const apiIncidentDetail = (id) => http.get(`/api/incidents/${id}`)

// ---------- 应急处置 ----------
export const apiDispatch = (id) => http.post(`/api/incidents/${id}/emergency/dispatch`)
export const apiSecure = (id, data) => http.post(`/api/incidents/${id}/emergency/secure`, data)

// ---------- 权属 ----------
export const apiInvestigate = (id, data) => http.post(`/api/incidents/${id}/investigation`, data)
export const apiClaim = (id, data) => http.post(`/api/incidents/${id}/claim`, data)

// ---------- 维修 ----------
export const apiCreateOrder = (id, data) => http.post(`/api/incidents/${id}/repair-order`, data)
export const apiMyOrders = () => http.get('/api/repair-orders')
export const apiAcceptOrder = (id) => http.post(`/api/repair-orders/${id}/accept`)
export const apiFinishOrder = (id, data) => http.post(`/api/repair-orders/${id}/finish`, data)

// ---------- 验收 ----------
export const apiAcceptance = (id, data) => http.post(`/api/incidents/${id}/acceptance`, data)

// ---------- 赔付 ----------
export const apiCompensations = () => http.get('/api/compensations')
export const apiCompDetail = (id) => http.get(`/api/compensations/${id}`)
export const apiCompUpdate = (id, data) => http.put(`/api/compensations/${id}`, data)
export const apiCompAccidentTime = (id, data) => http.post(`/api/compensations/${id}/accident-time`, data)
export const apiCompMaterialUpdate = (id, mid, data) => http.put(`/api/compensations/${id}/materials/${mid}`, data)
export const apiCompMaterialVerify = (id, mid, data) => http.post(`/api/compensations/${id}/materials/${mid}/verify`, data)
export const apiCompDecide = (id, data) => http.post(`/api/compensations/${id}/decide`, data)
export const apiCompPay = (id) => http.post(`/api/compensations/${id}/pay`)

// ---------- 费用分摊 ----------
export const apiCostAllocations = () => http.get('/api/cost-allocations')
export const apiCostReview = (id, data) => http.post(`/api/cost-allocations/${id}/review`, data)

// ---------- 统计 ----------
export const apiStatsOverview = () => http.get('/api/stats/overview')
export const apiStatsHotspots = () => http.get('/api/stats/hotspots')
export const apiStatsDisputes = () => http.get('/api/stats/disputes')
export const apiStatsTimeouts = () => http.get('/api/stats/repair-timeouts')
export const apiStatsCompensation = () => http.get('/api/stats/compensation')
export const apiStatsEmergency = () => http.get('/api/stats/emergency')

// ---------- 管理 ----------
export const apiUsers = () => http.get('/api/admin/users')
export const apiCreateUser = (data) => http.post('/api/admin/users', data)
export const apiRoutes = () => http.get('/api/admin/routes')
export const apiSaveRoute = (data) => http.post('/api/admin/routes', data)
export const apiDeleteRoute = (id) => http.delete(`/api/admin/routes/${id}`)
export const apiRules = () => http.get('/api/admin/rules')
export const apiSaveRule = (data) => http.post('/api/admin/rules', data)
export const apiDeleteRule = (id) => http.delete(`/api/admin/rules/${id}`)

// ---------- 通知 ----------
export const apiNotifications = () => http.get('/api/notifications')
export const apiReadAll = () => http.post('/api/notifications/read-all')

// ---------- 文件 ----------
export const apiUpload = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return http.post('/api/files', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

export default http
