import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons'
import { Button, Card, Col, Descriptions, Drawer, Form, FormInstance, Input, InputNumber, List, Modal, Row, Select, Space, Table, Tag, Typography, Upload, message } from 'antd'
import { useEffect, useState } from 'react'
import {
  addComment, Attachment, Comment, createTask, deleteAttachment, deleteTask, getTask, listAttachments, listComments, listProjects, listTasks, Project, Task, transferTask, transitionTask, updateTask, uploadAttachment,
} from '../api'

type Props = { can: (permission: string) => boolean }
const statuses = ['DRAFT', 'PENDING_ACCEPTANCE', 'IN_PROGRESS', 'PENDING_REVIEW', 'REJECTED', 'COMPLETED', 'CANCELLED', 'ARCHIVED']
const priorities = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']
const actions = ['submit', 'accept', 'start', 'submit-review', 'approve', 'complete', 'reject', 'cancel', 'archive']

export default function TaskPage({ can }: Props) {
  const [tasks, setTasks] = useState<Task[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [selected, setSelected] = useState<Task | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState('')
  const [filters, setFilters] = useState({ status: '', priority: '', title: '' })
  const [createForm] = Form.useForm()
  const [editForm] = Form.useForm()
  const [commentForm] = Form.useForm()
  const [transferForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try { const [page, projectList] = await Promise.all([listTasks(filters), listProjects()]); setTasks(page.records); setProjects(projectList) }
    catch (error) { message.error(error instanceof Error ? error.message : '任务加载失败') }
    finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [])

  const openTask = async (task: Task) => {
    try {
      const [detail, commentPage, attachmentPage] = await Promise.all([getTask(task.taskId), listComments(task.taskId), listAttachments(task.taskId).catch(() => ({ records: [] } as { records: Attachment[] }))])
      setSelected(detail); setComments(commentPage.records); setAttachments(attachmentPage.records)
      transferForm.setFieldsValue({ version: detail.version, primaryAssigneeId: detail.assignees.find((item) => item.assigneeType === 'PRIMARY')?.userId })
    } catch (error) { message.error(error instanceof Error ? error.message : '任务详情加载失败') }
  }

  const withBusy = async (key: string, work: () => Promise<void>) => { if (busy) return; setBusy(key); try { await work() } finally { setBusy('') } }
  const submitCreate = async (values: Record<string, unknown>) => withBusy('create', async () => {
    const body = { ...values, projectId: numberValue(values.projectId), departmentId: numberValue(values.departmentId), primaryAssigneeId: numberValue(values.primaryAssigneeId), collaboratorIds: csvIds(values.collaboratorIds) }
    try { await createTask(body); message.success('任务已创建'); setCreateOpen(false); createForm.resetFields(); await load() } catch (error) { message.error(error instanceof Error ? error.message : '任务创建失败') }
  })
  const submitEdit = async (values: Record<string, unknown>) => selected && withBusy('edit', async () => {
    try { const detail = await updateTask(selected.taskId, { ...values, version: selected.version }); message.success('任务已更新'); setEditOpen(false); setSelected(detail); await load() }
    catch (error) { message.error(error instanceof Error ? error.message : '任务更新失败') }
  })
  const runAction = (action: string) => selected && withBusy(action, async () => {
    try { const detail = await transitionTask(selected.taskId, action, selected.version); message.success(`任务已执行${action}`); setSelected(detail); await load() }
    catch (error) { message.error(error instanceof Error ? error.message : '任务状态操作失败') }
  })
  const remove = () => selected && withBusy('delete', async () => {
    try { await deleteTask(selected.taskId, selected.version); message.success('草稿已删除'); setSelected(null); await load() }
    catch (error) { message.error(error instanceof Error ? error.message : '删除失败') }
  })
  const submitTransfer = async (values: Record<string, unknown>) => selected && withBusy('transfer', async () => {
    try { const detail = await transferTask(selected.taskId, { version: selected.version, primaryAssigneeId: Number(values.primaryAssigneeId), collaboratorIds: csvIds(values.collaboratorIds) }); message.success('负责人已更新'); setSelected(detail); transferForm.setFieldValue('version', detail.version); await load() }
    catch (error) { message.error(error instanceof Error ? error.message : '负责人更新失败') }
  })
  const submitComment = async (values: { content: string }) => selected && withBusy('comment', async () => {
    try { const comment = await addComment(selected.taskId, values.content); setComments((current) => [...current, comment]); commentForm.resetFields(); message.success('评论已发布') }
    catch (error) { message.error(error instanceof Error ? error.message : '评论发布失败') }
  })

  return (
    <Space direction="vertical" size="large" className="page-stack">
      <div className="page-heading"><div><Typography.Title level={2}>任务中心</Typography.Title><Typography.Text type="secondary">列表、详情、状态、评论和附件统一在一个工作区完成。</Typography.Text></div>{can('task:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建任务</Button>}</div>
      <Card>
        <Space wrap>
          <Input placeholder="标题" value={filters.title} onChange={(event) => setFilters({ ...filters, title: event.target.value })} onPressEnter={() => void load()} />
          <Select allowClear placeholder="状态" style={{ width: 170 }} value={filters.status || undefined} options={statuses.map((value) => ({ value, label: value }))} onChange={(status) => setFilters({ ...filters, status: status || '' })} />
          <Select allowClear placeholder="优先级" style={{ width: 140 }} value={filters.priority || undefined} options={priorities.map((value) => ({ value, label: value }))} onChange={(priority) => setFilters({ ...filters, priority: priority || '' })} />
          <Button onClick={() => void load()} loading={loading}>查询</Button>
        </Space>
      </Card>
      <Card><Table rowKey="taskId" loading={loading} dataSource={tasks} pagination={{ pageSize: 20 }} columns={[
        { title: '任务', dataIndex: 'title', render: (value: string, task: Task) => <Button type="link" onClick={() => void openTask(task)}>{value}</Button> },
        { title: '编号', dataIndex: 'taskNo' }, { title: '状态', dataIndex: 'status', render: (value: string) => <Tag>{value}</Tag> },
        { title: '优先级', dataIndex: 'priority', render: (value: string) => <Tag color={value === 'URGENT' ? 'red' : 'blue'}>{value}</Tag> },
        { title: '版本', dataIndex: 'version' }, { title: '操作', render: (_: unknown, task: Task) => <Button icon={<EyeOutlined />} onClick={() => void openTask(task)}>详情</Button> },
      ]} /></Card>

      <Modal title="创建任务" open={createOpen} onCancel={() => setCreateOpen(false)} footer={null} destroyOnClose>
        <TaskForm form={createForm} projects={projects} onFinish={submitCreate} loading={busy === 'create'} submitText="创建" />
      </Modal>
      <Modal title="编辑任务草稿" open={editOpen} onCancel={() => setEditOpen(false)} footer={null} destroyOnClose>
        <Form form={editForm} layout="vertical" onFinish={submitEdit}><Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="description" label="描述"><Input.TextArea rows={4} /></Form.Item><Form.Item name="priority" label="优先级" rules={[{ required: true }]}><Select options={priorities.map((value) => ({ value, label: value }))} /></Form.Item><Form.Item name="dueAt" label="截止时间"><Input placeholder="2026-12-31T18:00:00" /></Form.Item><Button type="primary" htmlType="submit" loading={busy === 'edit'}>保存</Button></Form>
      </Modal>
      <Drawer title={selected ? `${selected.taskNo} · ${selected.title}` : '任务详情'} open={Boolean(selected)} onClose={() => setSelected(null)} width={720}>
        {selected && <Space direction="vertical" size="large" className="drawer-stack">
          <Descriptions bordered size="small" column={2}><Descriptions.Item label="状态"><Tag>{selected.status}</Tag></Descriptions.Item><Descriptions.Item label="优先级">{selected.priority}</Descriptions.Item><Descriptions.Item label="版本">{selected.version}</Descriptions.Item><Descriptions.Item label="描述">{selected.description || '暂无描述'}</Descriptions.Item></Descriptions>
          <Space wrap>{can('task:update') && selected.status === 'DRAFT' && <Button icon={<EditOutlined />} onClick={() => { editForm.setFieldsValue(selected); setEditOpen(true) }}>编辑</Button>}{can('task:delete') && selected.status === 'DRAFT' && <Button danger icon={<DeleteOutlined />} loading={busy === 'delete'} onClick={() => Modal.confirm({ title: '确认删除草稿？', onOk: remove })}>删除</Button>}{actions.map((action) => can(action === 'approve' || action === 'complete' ? 'task:approve' : `task:${action === 'submit-review' ? 'review' : action}`) && <Button key={action} loading={busy === action} onClick={() => void runAction(action)}>{action}</Button>)}</Space>
          {can('task:assign') && <Form form={transferForm} layout="inline" onFinish={submitTransfer}><Form.Item name="primaryAssigneeId" label="主负责人" rules={[{ required: true }]}><InputNumber min={1} /></Form.Item><Form.Item name="collaboratorIds" label="协作者 ID"><Input placeholder="1,2,3" /></Form.Item><Button htmlType="submit" loading={busy === 'transfer'}>保存负责人</Button></Form>}
          <Card title="评论"><List dataSource={comments} locale={{ emptyText: '暂无评论' }} renderItem={(comment) => <List.Item><List.Item.Meta title={comment.authorDisplayName} description={comment.content} /></List.Item>} />{can('task:comment:create') && <Form form={commentForm} layout="inline" onFinish={submitComment}><Form.Item name="content" rules={[{ required: true, message: '请输入评论' }]}><Input placeholder="写下评论" /></Form.Item><Button type="primary" htmlType="submit" loading={busy === 'comment'}>发布</Button></Form>}</Card>
          {can('task:attachment:read') && <Card title="附件"><List dataSource={attachments} locale={{ emptyText: '暂无附件' }} renderItem={(attachment) => <List.Item actions={[can('task:attachment:read') && <Button danger type="link" onClick={() => void withBusy(`attachment-${attachment.attachmentId}`, async () => { try { await deleteAttachment(selected.taskId, attachment.attachmentId); setAttachments((current) => current.filter((item) => item.attachmentId !== attachment.attachmentId)); message.success('附件已删除') } catch (error) { message.error(error instanceof Error ? error.message : '附件删除失败') } })}>删除</Button>]}>{attachment.originalFilename}</List.Item>} /><Upload showUploadList={false} beforeUpload={(file) => { void withBusy('upload', async () => { try { const item = await uploadAttachment(selected.taskId, file); setAttachments((current) => [...current, item]); message.success('附件已上传') } catch (error) { message.error(error instanceof Error ? error.message : '附件上传失败') } }); return false }}><Button icon={<UploadOutlined />} loading={busy === 'upload'}>上传附件</Button></Upload></Card>}
        </Space>}
      </Drawer>
    </Space>
  )
}

function TaskForm({ form, projects, onFinish, loading, submitText }: { form: FormInstance; projects: Project[]; onFinish: (values: Record<string, unknown>) => void; loading: boolean; submitText: string }) {
  return <Form form={form} layout="vertical" onFinish={onFinish}><Form.Item name="taskNo" label="任务编号" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_-]{2,63}$/ }]}><Input /></Form.Item><Form.Item name="title" label="标题" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="description" label="描述"><Input.TextArea rows={4} /></Form.Item><Row gutter={12}><Col span={12}><Form.Item name="priority" label="优先级" initialValue="MEDIUM" rules={[{ required: true }]}><Select options={priorities.map((value) => ({ value, label: value }))} /></Form.Item></Col><Col span={12}><Form.Item name="projectId" label="项目"><Select allowClear options={projects.map((project) => ({ value: project.projectId, label: project.projectName }))} /></Form.Item></Col></Row><Form.Item name="dueAt" label="截止时间"><Input placeholder="2026-12-31T18:00:00" /></Form.Item><Form.Item name="primaryAssigneeId" label="主负责人 ID"><InputNumber min={1} /></Form.Item><Form.Item name="collaboratorIds" label="协作者 ID"><Input placeholder="1,2,3" /></Form.Item><Button type="primary" htmlType="submit" loading={loading}>{submitText}</Button></Form>
}

function numberValue(value: unknown) { return value === undefined || value === null || value === '' ? undefined : Number(value) }
function csvIds(value: unknown) { return typeof value === 'string' && value.trim() ? value.split(',').map((item) => Number(item.trim())).filter((item) => Number.isFinite(item)) : [] }
