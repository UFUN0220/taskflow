import { CheckCircleOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Card, Col, Form, Input, Modal, Row, Space, Table, Tag, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import { createProject, listProjects, Project } from '../api'

type Props = { canCreateProject: boolean }

export default function DashboardPage({ canCreateProject }: Props) {
  const [projects, setProjects] = useState<Project[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [form] = Form.useForm()

  const load = async () => { try { setProjects(await listProjects()) } catch (error) { message.error(error instanceof Error ? error.message : '项目加载失败') } }
  useEffect(() => { void load() }, [])
  const submit = async (values: { projectCode: string; projectName: string; departmentId?: number }) => {
    setLoading(true)
    try { await createProject(values); message.success('项目已创建'); setOpen(false); form.resetFields(); await load() }
    catch (error) { message.error(error instanceof Error ? error.message : '项目创建失败') }
    finally { setLoading(false) }
  }

  return (
    <Space direction="vertical" size="large" className="page-stack">
      <div className="page-heading">
        <div><Tag color="blue">阶段 13</Tag><Typography.Title level={2}>工作台</Typography.Title><Typography.Paragraph type="secondary">从项目和任务开始协作，所有写操作都会经过后端权限校验。</Typography.Paragraph></div>
        {canCreateProject && <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>创建项目</Button>}
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Card><CheckCircleOutlined className="status-icon" /><Typography.Title level={4}>业务流程已连接</Typography.Title><Typography.Text type="secondary">登录、任务、评论、附件和通知使用统一 API 客户端。</Typography.Text></Card></Col>
        <Col xs={24} md={8}><Card><Typography.Title level={4}>项目数</Typography.Title><Typography.Title>{projects.length}</Typography.Title></Card></Col>
        <Col xs={24} md={8}><Card><Typography.Title level={4}>权限控制</Typography.Title><Typography.Text type="secondary">菜单和按钮只做体验控制，最终权限由后端决定。</Typography.Text></Card></Col>
      </Row>
      <Card title="我的项目"><Table rowKey="projectId" dataSource={projects} pagination={false} columns={[
        { title: '项目编码', dataIndex: 'projectCode' }, { title: '项目名称', dataIndex: 'projectName' },
        { title: '状态', dataIndex: 'status', render: (value: string) => <Tag color="green">{value}</Tag> },
      ]} /></Card>
      <Modal title="创建项目" open={open} onCancel={() => setOpen(false)} footer={null} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={submit} initialValues={{ projectCode: '', projectName: '' }}>
          <Form.Item name="projectCode" label="项目编码" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_-]{2,63}$/, message: '使用大写字母开头的编码' }]}><Input /></Form.Item>
          <Form.Item name="projectName" label="项目名称" rules={[{ required: true, max: 200 }]}><Input /></Form.Item>
          <Form.Item name="departmentId" label="部门 ID"><Input type="number" /></Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>保存</Button>
        </Form>
      </Modal>
    </Space>
  )
}
