import { DownloadOutlined } from "@ant-design/icons";
import { Alert, Button, Modal, Space, Typography } from "antd";
import { useEffect, useRef, useState } from "react";

import type { AsyncExportActionProps, AsyncExportJob } from "./experienceTypes";

const { Text } = Typography;
const DEFAULT_POLL_DELAY_MS = 2000;

function shouldPoll(job: AsyncExportJob): boolean {
  return job.status === "pending" || job.status === "running";
}

function jobStatusMessage(job: AsyncExportJob): string {
  switch (job.status) {
    case "pending":
      return "导出任务已提交";
    case "running":
      return "导出任务运行中";
    case "succeeded":
      return "导出已完成";
    case "failed":
      return "导出任务失败";
    case "expired":
      return "导出结果已过期";
    default:
      return "导出任务不可用";
  }
}

export function AsyncExportAction({
  enabled,
  disabledReason,
  permissionGranted,
  request,
  onSubmit,
  onPoll,
  pollDelayMs = DEFAULT_POLL_DELAY_MS,
}: AsyncExportActionProps) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [job, setJob] = useState<AsyncExportJob>();
  const [failure, setFailure] = useState<string>();
  const pollingRef = useRef(false);
  const activeRequestRef = useRef<AsyncExportActionProps["request"]>();

  useEffect(() => {
    if (!job || !shouldPoll(job) || !onPoll) {
      return;
    }

    let cancelled = false;
    const poll = () => {
      if (pollingRef.current) return;
      pollingRef.current = true;
      void onPoll(job.jobId)
        .then((nextJob) => {
          if (!cancelled) {
            setJob(nextJob);
          }
        })
        .catch((error: unknown) => {
          if (!cancelled) {
            setFailure(error instanceof Error ? error.message : "轮询导出任务失败");
          }
        })
        .finally(() => {
          pollingRef.current = false;
        });
    };

    poll();
    const timer = window.setInterval(poll, pollDelayMs);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [job, onPoll, pollDelayMs]);

  async function submitRequest() {
    if (!onSubmit) {
      setFailure("导出服务尚未接入");
      return;
    }

    setSubmitting(true);
    setFailure(undefined);
    pollingRef.current = false;
    const submitRequestPayload =
      activeRequestRef.current ??
      ({
        ...request,
        idempotencyKey: request.idempotencyKey ?? crypto.randomUUID(),
      } satisfies AsyncExportActionProps["request"]);
    activeRequestRef.current = submitRequestPayload;
    try {
      const nextJob = await onSubmit(submitRequestPayload);
      setJob(nextJob);
      setConfirmOpen(false);
      activeRequestRef.current = undefined;
    } catch (error) {
      setFailure(error instanceof Error ? error.message : "导出提交失败");
      setConfirmOpen(false);
    } finally {
      setSubmitting(false);
    }
  }

  if (!enabled) {
    return (
      <Space>
        <Button aria-label="导出" icon={<DownloadOutlined />} disabled>
          导出
        </Button>
        <Text type="secondary">{disabledReason ?? "导出任务接口尚未接入"}</Text>
      </Space>
    );
  }

  if (!permissionGranted) {
    return (
      <Space>
        <Button aria-label="导出" icon={<DownloadOutlined />} disabled>
          导出
        </Button>
        <Text type="secondary">当前权限不足，无法提交导出任务</Text>
      </Space>
    );
  }

  return (
    <Space direction="vertical" size="small">
      <Button
        aria-label="导出"
        icon={<DownloadOutlined />}
        onClick={() => {
          activeRequestRef.current = undefined;
          setConfirmOpen(true);
        }}
      >
        导出
      </Button>
      <Modal
        title="提交导出任务"
        open={confirmOpen}
        okText="提交导出任务"
        okButtonProps={{ "aria-label": "提交导出任务" }}
        cancelText="取消"
        confirmLoading={submitting}
        onOk={() => void submitRequest()}
        onCancel={() => setConfirmOpen(false)}
      >
        <Text>导出范围将按当前视图快照记录并留痕。</Text>
      </Modal>
      {failure && (
        <Alert
          type="error"
          showIcon
          message="导出提交失败"
          description={failure}
          action={
            <Button size="small" aria-label="重试导出" onClick={() => void submitRequest()}>
              重试
            </Button>
          }
        />
      )}
      {job && (
        <Alert
          type={job.status === "failed" ? "error" : "info"}
          showIcon
          message={jobStatusMessage(job)}
          description={
            <Space direction="vertical" size={0}>
              <Text>任务编号：{job.jobId}</Text>
              {job.traceId && <Text>traceId：{job.traceId}</Text>}
              {job.auditId && <Text>审计编号：{job.auditId}</Text>}
              {job.failureReason && <Text>{job.failureReason}</Text>}
              {job.downloadUrl && (
                <Button type="link" href={job.downloadUrl}>
                  下载导出文件
                </Button>
              )}
            </Space>
          }
        />
      )}
    </Space>
  );
}
