import { BellOutlined, CheckOutlined, ReloadOutlined } from '@ant-design/icons'
import { Badge, Button, Drawer, Empty, List, Space, Tag, Typography, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import {
  currentAccessToken,
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  NotificationItem,
  NotificationRealtimeClient,
  NotificationSocketStatus,
} from './notificationClient'

function mergeNotifications(current: NotificationItem[], incoming: NotificationItem) {
  const key = incoming.notificationId ?? incoming.sourceMessageId
  return [incoming, ...current.filter((item) => (item.notificationId ?? item.sourceMessageId) !== key)]
}

export default function NotificationCenter() {
  const [open, setOpen] = useState(false)
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [status, setStatus] = useState<NotificationSocketStatus>('DISCONNECTED')
  const [loading, setLoading] = useState(false)
  const token = useMemo(() => currentAccessToken(), [])

  const loadUnread = async () => {
    if (!token) return
    setLoading(true)
    try {
      const [items, count] = await Promise.all([fetchNotifications(token), fetchUnreadCount(token)])
      setNotifications(items)
      setUnreadCount(count)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '通知加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!token) return
    void loadUnread()
    const client = new NotificationRealtimeClient(
      token,
      (notification) => {
        setNotifications((current) => mergeNotifications(current, notification))
        if (notification.status === 'UNREAD') setUnreadCount((count) => count + 1)
      },
      (nextStatus) => {
        setStatus(nextStatus)
        if (nextStatus === 'CONNECTED') void loadUnread()
      },
    )
    client.connect()
    return () => client.disconnect()
  }, [token])

  const markRead = async (notification: NotificationItem) => {
    if (!token || !notification.notificationId || notification.status === 'READ') return
    try {
      await markNotificationRead(token, notification.notificationId)
      setNotifications((current) => current.map((item) => item.notificationId === notification.notificationId
        ? { ...item, status: 'READ' }
        : item))
      setUnreadCount((count) => Math.max(0, count - 1))
    } catch (error) {
      message.error(error instanceof Error ? error.message : '通知已读失败')
    }
  }

  const markAllRead = async () => {
    if (!token || unreadCount === 0) return
    try {
      await markAllNotificationsRead(token)
      setNotifications((current) => current.map((item) => ({ ...item, status: 'READ' })))
      setUnreadCount(0)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '通知全部已读失败')
    }
  }

  return (
    <>
      <Button type="text" onClick={() => setOpen(true)} disabled={!token} aria-label="打开通知中心">
        <Badge count={unreadCount} overflowCount={99} size="small">
          <BellOutlined />
        </Badge>
      </Button>
      <Drawer
        title="通知中心"
        open={open}
        onClose={() => setOpen(false)}
        width={420}
        extra={(
          <Space>
            <Tag color={status === 'CONNECTED' ? 'green' : status === 'CONNECTING' ? 'gold' : 'default'}>
              {status === 'CONNECTED' ? '实时连接' : status === 'CONNECTING' ? '连接中' : 'HTTP补拉'}
            </Tag>
            <Button icon={<ReloadOutlined />} onClick={() => void loadUnread()} loading={loading} />
            <Button icon={<CheckOutlined />} onClick={() => void markAllRead()} disabled={unreadCount === 0} />
          </Space>
        )}
      >
        {!token ? <Empty description="登录后查看通知" /> : (
          <List
            dataSource={notifications}
            locale={{ emptyText: '暂无未读通知' }}
            renderItem={(notification) => (
              <List.Item
                actions={notification.status === 'UNREAD' && notification.notificationId
                  ? [<Button type="link" onClick={() => void markRead(notification)}>标记已读</Button>]
                  : []}
              >
                <List.Item.Meta
                  title={<Typography.Text strong={notification.status === 'UNREAD'}>{notification.title}</Typography.Text>}
                  description={(
                    <Space direction="vertical" size={2}>
                      <Typography.Text>{notification.content}</Typography.Text>
                      <Typography.Text type="secondary">{notification.createdAt ?? '刚刚'}</Typography.Text>
                    </Space>
                  )}
                />
              </List.Item>
            )}
          />
        )}
      </Drawer>
    </>
  )
}
