import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import { assignUserRoles, createDepartment, createRole, createUser, DepartmentNode, listDepartments, listRoles, listUsers, Role, updateRole, updateUserStatus, User } from '../api'

type Props = { can: (permission: string) => boolean }
const scopes = ['SELF', 'DEPARTMENT', 'DEPARTMENT_AND_CHILDREN', 'PROJECT', 'ALL']

export default function ManagementPage({ can }: Props) {
  const [users, setUsers] = useState<User[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [departments, setDepartments] = useState<DepartmentNode[]>([])
  const [userOpen, setUserOpen] = useState(false)
  const [roleOpen, setRoleOpen] = useState(false)
  const [departmentOpen, setDepartmentOpen] = useState(false)
  const [editingRole, setEditingRole] = useState<Role | null>(null)
  const [busy, setBusy] = useState(false)
  const [userForm] = Form.useForm()
  const [roleForm] = Form.useForm()
  const [departmentForm] = Form.useForm()

  const load = async () => {
    try {
      const [userPage, roleList, departmentTree] = await Promise.all([
        can('user:read') ? listUsers() : Promise.resolve({ records: [] } as { records: User[] }),
        can('role:read') ? listRoles() : Promise.resolve([]),
        can('department:read') ? listDepartments() : Promise.resolve([]),
      ])
      setUsers(userPage.records); setRoles(roleList); setDepartments(departmentTree)
    } catch (error) { message.error(error instanceof Error ? error.message : '管理数据加载失败') }
  }
  useEffect(() => { void load() }, [])
  const run = async (work: () => Promise<void>) => { if (busy) return; setBusy(true); try { await work(); await load() } catch (error) { message.error(error instanceof Error ? error.message : '操作失败') } finally { setBusy(false) } }
  const createUserAction = (values: Record<string, unknown>) => run(async () => { await createUser({ ...values, roleCodes: csv(values.roleCodes) }); userForm.resetFields(); setUserOpen(false); message.success('用户已创建') })
  const createRoleAction = (values: Record<string, unknown>) => run(async () => { if (editingRole) { await updateRole(editingRole.roleId, { roleName: values.roleName, status: values.status, permissionCodes: csv(values.permissionCodes), scopeType: values.scopeType, version: editingRole.version }); message.success('角色已更新') } else { await createRole({ ...values, permissionCodes: csv(values.permissionCodes) }); message.success('角色已创建') } roleForm.resetFields(); setEditingRole(null); setRoleOpen(false) })
  const createDepartmentAction = (values: Record<string, unknown>) => run(async () => { await createDepartment({ ...values, parentId: values.parentId ? Number(values.parentId) : undefined }); departmentForm.resetFields(); setDepartmentOpen(false); message.success('部门已创建') })

  return <Space direction="vertical" size="large" className="page-stack"><div className="page-heading"><div><Typography.Title level={2}>组织与权限</Typography.Title><Typography.Text type="secondary">用户、角色、部门的操作仍由后端 RBAC 和数据范围独立校验。</Typography.Text></div><Button icon={<ReloadOutlined />} onClick={() => void load()}>刷新</Button></div><Tabs items={[
    can('user:read') && { key: 'users', label: '用户', children: <Card extra={can('user:write') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setUserOpen(true)}>创建用户</Button>}><Table rowKey="userId" dataSource={users} columns={[{ title: '姓名', dataIndex: 'displayName' }, { title: '用户名', dataIndex: 'username' }, { title: '工号', dataIndex: 'employeeNo' }, { title: '状态', dataIndex: 'status', render: (value: string) => <Tag>{value}</Tag> }, { title: '版本', dataIndex: 'version' }, { title: '操作', render: (_: unknown, user: User) => can('user:write') ? <Space><Select size="small" value={user.status} options={['ACTIVE', 'DISABLED', 'LOCKED'].map((value) => ({ value, label: value }))} onChange={(status) => void run(async () => { await updateUserStatus(user.userId, { status, version: user.version }); message.success('用户状态已更新') })} /><Button size="small" onClick={() => { const roleCodes = window.prompt('输入角色编码，使用逗号分隔', 'employee'); if (roleCodes) void run(async () => { await assignUserRoles(user.userId, { roleCodes: csv(roleCodes), version: user.version }); message.success('用户角色已更新') }) }}>分配角色</Button></Space> : null }]} /></Card> } as const,
    can('role:read') && { key: 'roles', label: '角色', children: <Card extra={can('role:write') && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingRole(null); roleForm.resetFields(); setRoleOpen(true) }}>创建角色</Button>}><Table rowKey="roleId" dataSource={roles} columns={[{ title: '编码', dataIndex: 'roleCode' }, { title: '名称', dataIndex: 'roleName' }, { title: '数据范围', dataIndex: 'scopeType' }, { title: '权限', dataIndex: 'permissionCodes', render: (items: string[]) => items.join(', ') }, { title: '内置', dataIndex: 'builtIn', render: (value: boolean) => value ? <Tag color="blue">是</Tag> : '否' }, { title: '操作', render: (_: unknown, role: Role) => can('role:write') && !role.builtIn ? <Button onClick={() => { setEditingRole(role); roleForm.setFieldsValue({ ...role, permissionCodes: role.permissionCodes.join(', ') }); setRoleOpen(true) }}>编辑</Button> : null }]} /></Card> } as const,
    can('department:read') && { key: 'departments', label: '部门', children: <Card extra={can('department:write') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setDepartmentOpen(true)}>创建部门</Button>}><Table rowKey="id" dataSource={flatten(departments)} columns={[{ title: '编码', dataIndex: 'departmentCode' }, { title: '部门名称', dataIndex: 'departmentName' }, { title: '层级', dataIndex: 'level' }]} /></Card> } as const,
  ].filter(Boolean) as { key: string; label: string; children: JSX.Element }[]} />
  <Modal title="创建用户" open={userOpen} onCancel={() => setUserOpen(false)} footer={null} destroyOnClose><Form form={userForm} layout="vertical" onFinish={createUserAction}><Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="employeeNo" label="工号" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="displayName" label="姓名" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="password" label="初始密码" rules={[{ required: true, min: 8 }]}><Input.Password /></Form.Item><Form.Item name="departmentId" label="部门 ID"><Input type="number" /></Form.Item><Form.Item name="roleCodes" label="角色编码" initialValue="employee"><Input /></Form.Item><Button type="primary" htmlType="submit" loading={busy}>保存</Button></Form></Modal>
  <Modal title={editingRole ? '编辑角色' : '创建角色'} open={roleOpen} onCancel={() => { setRoleOpen(false); setEditingRole(null) }} footer={null} destroyOnClose><Form form={roleForm} layout="vertical" onFinish={createRoleAction}><Form.Item name="roleCode" label="角色编码" rules={[{ required: true }]}><Input disabled={Boolean(editingRole)} /></Form.Item><Form.Item name="roleName" label="角色名称" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="status" label="状态" initialValue="ACTIVE"><Select options={['ACTIVE', 'DISABLED'].map((value) => ({ value, label: value }))} /></Form.Item><Form.Item name="permissionCodes" label="权限编码" rules={[{ required: true }]}><Input.TextArea placeholder="user:read,task:read" /></Form.Item><Form.Item name="scopeType" label="数据范围" initialValue="SELF" rules={[{ required: true }]}><Select options={scopes.map((value) => ({ value, label: value }))} /></Form.Item><Button type="primary" htmlType="submit" loading={busy}>保存</Button></Form></Modal>
  <Modal title="创建部门" open={departmentOpen} onCancel={() => setDepartmentOpen(false)} footer={null} destroyOnClose><Form form={departmentForm} layout="vertical" onFinish={createDepartmentAction}><Form.Item name="departmentCode" label="部门编码" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="departmentName" label="部门名称" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="parentId" label="父部门 ID"><Input type="number" /></Form.Item><Button type="primary" htmlType="submit" loading={busy}>保存</Button></Form></Modal>
  </Space>
}

function csv(value: unknown) { return typeof value === 'string' ? value.split(',').map((item) => item.trim()).filter(Boolean) : [] }
function flatten(nodes: DepartmentNode[], level = 1): DepartmentNode[] { return nodes.flatMap((node) => [{ ...node, level }, ...flatten(node.children || [], level + 1)]) }
