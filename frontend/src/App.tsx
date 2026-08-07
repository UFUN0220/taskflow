import { CheckCircleOutlined, ProjectOutlined } from '@ant-design/icons'
import { Layout, Menu, Space, Tag, Typography } from 'antd'
import { Route, Routes, useNavigate } from 'react-router-dom'

const { Header, Sider, Content } = Layout

function DashboardPage() {
  return (
    <div className="dashboard-page">
      <Space direction="vertical" size="large">
        <div>
          <Tag color="blue">阶段 0</Tag>
          <Typography.Title level={2}>项目初始化完成</Typography.Title>
          <Typography.Paragraph type="secondary">
            TaskFlow 是面向企业内部协作场景的任务与流程管理平台。
          </Typography.Paragraph>
        </div>
        <div className="status-card">
          <CheckCircleOutlined className="status-icon" />
          <div>
            <Typography.Text strong>开发环境就绪</Typography.Text>
            <Typography.Paragraph type="secondary">
              后端健康检查：GET /api/health
            </Typography.Paragraph>
          </div>
        </div>
      </Space>
    </div>
  )
}

export default function App() {
  const navigate = useNavigate()

  return (
    <Layout className="app-layout">
      <Sider breakpoint="lg" collapsedWidth="0">
        <div className="brand">TaskFlow</div>
        <Menu
          theme="dark"
          mode="inline"
          defaultSelectedKeys={['dashboard']}
          onClick={() => navigate('/')}
          items={[{ key: 'dashboard', icon: <ProjectOutlined />, label: '工作台' }]}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Typography.Title level={4}>企业任务协同与流程管理平台</Typography.Title>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="*" element={<DashboardPage />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}
