-- Stage 15: run against a non-production copy of the TaskFlow database.
-- Replace literals with the actual values used by the benchmark run.

EXPLAIN
SELECT id, task_no, title, description, project_id, department_id, creator_id,
       status, priority, due_at, created_at, updated_at, version
FROM task
WHERE deleted = 0
  AND status = 'DRAFT'
ORDER BY id DESC
LIMIT 20 OFFSET 0;

EXPLAIN
SELECT id, task_no, title, description, project_id, department_id, creator_id,
       status, priority, due_at, created_at, updated_at, version
FROM task
WHERE id = 1
  AND deleted = 0;

EXPLAIN
SELECT id, user_id, notification_type, title, content, aggregate_type,
       aggregate_id, read_at, status, created_at
FROM notification
WHERE user_id = 1
  AND deleted = 0
  AND status = 'UNREAD'
ORDER BY id DESC
LIMIT 20 OFFSET 0;

-- Inspect actual index choices and row estimates after the baseline.
SHOW INDEX FROM task;
SHOW INDEX FROM notification;
