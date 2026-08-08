import { LogoutOutlined, ProjectOutlined, SettingOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { Button, Layout, Menu, Space, Spin, Typography, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { clearAccessToken, currentUser, CurrentUser, getAccessToken } from './api'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import ManagementPage from './pages/ManagementPage'
import TaskPage from './pages/TaskPage'
import NotificationCenter from './notifications/NotificationCenter'

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
  if (!token || !user) return <Routes><Route path="*" element={<LoginPage onLoggedIn={() => void loadSession()} />} /></Routes>

  const logout = () => { clearAccessToken(); setToken(null); setUser(null); navigate('/login'); message.success('已退出登录') }
  const menuItems = [
    can('task:read') && { key: '/tasks', icon: <UnorderedListOutlined />, label: '任务中心' },
    can('project:read') && { key: '/', icon: <ProjectOutlined />, label: '工作台' },
    (can('user:read') || can('role:read') || can('department:read')) && { key: '/management', icon: <SettingOutlined />, label: '组织与权限' },
  ].filter(Boolean) as { key: string; icon: JSX.Element; label: string }[]

  return <Layout className="app-layout"><Sider breakpoint="lg" collapsedWidth="0"><div className="brand">TaskFlow</div><Menu theme="dark" mode="inline" selectedKeys={[location.pathname]} items={menuItems} onClick={({ key }) => navigate(key)} /></Sider><Layout><Header className="app-header"><Typography.Title level={4}>企业任务协同与流程管理平台</Typography.Title><div className="header-actions"><NotificationCenter /><Typography.Text>{user.displayName}</Typography.Text><Button type="text" icon={<LogoutOutlined />} onClick={logout}>退出</Button></div></Header><Content className="app-content"><Routes><Route path="/" element={<DashboardPage canCreateProject={can('project:write')} />} /><Route path="/tasks" element={<TaskPage can={can} />} /><Route path="/management" element={<ManagementPage can={can} />} /><Route path="*" element={<DashboardPage canCreateProject={can('project:write')} />} /></Routes></Content></Layout></Layout>
}
