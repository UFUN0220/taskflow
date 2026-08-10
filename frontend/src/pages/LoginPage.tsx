import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Button, Card, Form, Input, Typography, message } from 'antd'
import { useState } from 'react'
import { login, markBrowserAuthenticated } from '../api'

type Props = { onLoggedIn: () => void }

export default function LoginPage({ onLoggedIn }: Props) {
  const [loading, setLoading] = useState(false)
  const submit = async (values: { login: string; password: string }) => {
    setLoading(true)
    try {
      await login(values.login, values.password)
      markBrowserAuthenticated()
      message.success('登录成功')
      onLoggedIn()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-page">
      <Card className="login-card">
        <Typography.Title level={2}>TaskFlow</Typography.Title>
        <Typography.Paragraph type="secondary">企业任务协同与流程管理平台</Typography.Paragraph>
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item name="login" label="用户名或工号" rules={[{ required: true, message: '请输入用户名或工号' }]}>
            <Input prefix={<UserOutlined />} autoComplete="username" placeholder="请输入用户名或工号" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" placeholder="请输入密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>登录</Button>
        </Form>
      </Card>
    </main>
  )
}
