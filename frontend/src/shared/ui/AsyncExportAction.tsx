import { DownloadOutlined } from "@ant-design/icons";
import { Alert, Button, Modal, Space, Typography } from "antd";
import { useEffect, useRef, useState } from "react";

import { customerSafeDisplayText } from "@/shared/config/customerLabels";

import type { AsyncExportActionProps, AsyncExportJob } from "./experienceTypes";

const { Text } = Typography;
const DEFAULT_POLL_DELAY_MS = 2000;
const EXPORT_SUBMIT_FAILURE_FALLBACK = "导出服务暂时不可用，请重试或联系信息科。";
const EXPORT_POLL_FAILURE_FALLBACK = "导出任务状态读取失败，请重试或联系信息科。";
const EXPORT_JOB_FAILURE_FALLBACK = "导出任务未完成，请重试或联系信息科。";
const EXPORT_EVIDENCE_HINT = "导出证据已留痕，可在审计证据中追溯。";

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

function exportFailureText(error: unknown, fallback: string): string {
  return customerSafeDisplayText(error instanceof Error ? error.message : undefined, fallback);
}

export function AsyncExportAction({
  enabled,
  disabledReason,
  permissionGranted,
  request,
  onSubmit,
  onPoll,
  pollDelayMs = DEFAULT_POLL_DELAY_MS,
  buttonLabel = "导出",
  buttonAriaLabel = "导出",
  modalTitle = "提交导出任务",
  submitLabel = "提交导出任务",
}: AsyncExportActionProps) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [job, setJob] = useState<AsyncExportJob>();
  const [failure, setFailure] = useState<string>();
  const evidenceDetailsEnabled = request.requestSnapshot.evidenceDetailsEnabled;
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
            setFailure(exportFailureText(error, EXPORT_POLL_FAILURE_FALLBACK));
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
      setFailure("导出服务暂时不可用，请联系信息科确认导出配置。");
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
      setFailure(exportFailureText(error, EXPORT_SUBMIT_FAILURE_FALLBACK));
      setConfirmOpen(false);
    } finally {
      setSubmitting(false);
    }
  }

  if (!enabled) {
    return (
      <Space>
        <Button aria-label={buttonAriaLabel} icon={<DownloadOutlined />} disabled>
          {buttonLabel}
        </Button>
        <Text type="secondary">
          {disabledReason ?? "导出任务暂不可用，请联系信息科确认导出范围。"}
        </Text>
      </Space>
    );
  }

  if (!permissionGranted) {
    return (
      <Space>
        <Button aria-label={buttonAriaLabel} icon={<DownloadOutlined />} disabled>
          {buttonLabel}
        </Button>
        <Text type="secondary">当前权限不足，无法提交导出任务</Text>
      </Space>
    );
  }

  return (
    <Space direction="vertical" size="small">
      <Button
        aria-label={buttonAriaLabel}
        icon={<DownloadOutlined />}
        onClick={() => {
          activeRequestRef.current = undefined;
          setConfirmOpen(true);
        }}
      >
        {buttonLabel}
      </Button>
      <Modal
        title={modalTitle}
        open={confirmOpen}
        okText={submitLabel}
        okButtonProps={{ "aria-label": submitLabel }}
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
              <Text>
                {evidenceDetailsEnabled ? `导出任务编号：${job.jobId}` : "导出任务已登记"}
              </Text>
              {(job.traceId || job.auditId) && <Text>{EXPORT_EVIDENCE_HINT}</Text>}
              {job.failureReason && (
                <Text>
                  {customerSafeDisplayText(job.failureReason, EXPORT_JOB_FAILURE_FALLBACK)}
                </Text>
              )}
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
