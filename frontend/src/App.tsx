import { LogoutOutlined, ProjectOutlined, SettingOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { Button, Layout, Menu, Space, Spin, Typography, message } from 'antd'
import { lazy, Suspense, useEffect, useMemo, useState } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, clearAccessToken, currentUser, CurrentUser, getAccessToken, logout as serverLogout } from './api'
import NotificationCenter from './notifications/NotificationCenter'

const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const ManagementPage = lazy(() => import('./pages/ManagementPage'))
const TaskPage = lazy(() => import('./pages/TaskPage'))

const { Header, Sider, Content } = Layout

export default function App() {
  const navigate = useNavigate()
  const location = useLocation()
  const [token, setToken] = useState(getAccessToken())
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [checking, setChecking] = useState(Boolean(token))

  const loadSession = async () => {
    const currentToken = getAccessToken()
    if (!currentToken) { setToken(null); setUser(null); setChecking(false); return }
    setChecking(true)
    try { setToken(currentToken); setUser(await currentUser()) }
    catch (error) { clearAccessToken(); setToken(null); setUser(null); if (error instanceof Error) message.error(error.message) }
    finally { setChecking(false) }
  }
  useEffect(() => { void loadSession() }, [])
  useEffect(() => { const handler = () => { setToken(null); setUser(null); navigate('/login') }; window.addEventListener('taskflow:unauthorized', handler); return () => window.removeEventListener('taskflow:unauthorized', handler) }, [navigate])

  const permissions = useMemo(() => new Set(user?.authorities || []), [user])
  const can = (permission: string) => permissions.has(permission)
  if (checking) return <div className="page-loading"><Spin size="large" /></div>
  if (!token || !user) return <Suspense fallback={<div className="page-loading"><Spin size="large" /></div>}><Routes><Route path="*" element={<LoginPage onLoggedIn={() => void loadSession()} />} /></Routes></Suspense>

  const logout = async () => {
    try { await serverLogout() }
    catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) {
        message.warning('服务器未确认退出，当前令牌将在过期后失效')
      }
    }
    finally { clearAccessToken(); setToken(null); setUser(null); navigate('/login'); message.success('已退出登录') }
  }
  const menuItems = [
    can('task:read') && { key: '/tasks', icon: <UnorderedListOutlined />, label: '任务中心' },
    can('project:read') && { key: '/', icon: <ProjectOutlined />, label: '工作台' },
    (can('user:read') || can('role:read') || can('department:read')) && { key: '/management', icon: <SettingOutlined />, label: '组织与权限' },
  ].filter(Boolean) as { key: string; icon: JSX.Element; label: string }[]

  return <Layout className="app-layout"><Sider breakpoint="lg" collapsedWidth="0"><div className="brand">TaskFlow</div><Menu theme="dark" mode="inline" selectedKeys={[location.pathname]} items={menuItems} onClick={({ key }) => navigate(key)} /></Sider><Layout><Header className="app-header"><Typography.Title level={4}>企业任务协同与流程管理平台</Typography.Title><div className="header-actions"><NotificationCenter /><Typography.Text>{user.displayName}</Typography.Text><Button type="text" icon={<LogoutOutlined />} onClick={logout}>退出</Button></div></Header><Content className="app-content"><Suspense fallback={<div className="page-loading"><Spin size="large" /></div>}><Routes><Route path="/" element={<DashboardPage canCreateProject={can('project:write')} />} /><Route path="/tasks" element={<TaskPage can={can} />} /><Route path="/management" element={<ManagementPage can={can} />} /><Route path="*" element={<DashboardPage canCreateProject={can('project:write')} />} /></Routes></Suspense></Content></Layout></Layout>
}
