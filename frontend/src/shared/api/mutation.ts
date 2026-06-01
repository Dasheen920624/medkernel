import { useMutation, type UseMutationOptions } from "@tanstack/react-query";
import { message } from "antd";

import {
  applyApiFieldErrors,
  getApiErrorMessage,
  type ApiFieldErrorOptions,
  type ApiFormLike,
} from "./errors";

export interface ApiMutationFeedbackOptions extends ApiFieldErrorOptions {
  form?: ApiFormLike;
  fallbackErrorMessage?: string;
  showErrorMessage?: boolean;
  showMessageForFieldErrors?: boolean;
}

export type ApiMutationOptions<TData, TError, TVariables, TContext> = UseMutationOptions<
  TData,
  TError,
  TVariables,
  TContext
> & {
  feedback?: ApiMutationFeedbackOptions;
};

export function useApiMutation<
  TData = unknown,
  TError = unknown,
  TVariables = void,
  TContext = unknown,
>(options: ApiMutationOptions<TData, TError, TVariables, TContext>) {
  const { feedback, onError, ...mutationOptions } = options;

  return useMutation<TData, TError, TVariables, TContext>({
    ...mutationOptions,
    onError: (error, variables, context) => {
      const fieldErrorsApplied = feedback?.form
        ? applyApiFieldErrors(feedback.form, error, feedback)
        : false;
      const shouldShowError =
        feedback?.showErrorMessage !== false &&
        (!fieldErrorsApplied || feedback?.showMessageForFieldErrors === true);

      if (shouldShowError) {
        void message.error(
          getApiErrorMessage(error, feedback?.fallbackErrorMessage ?? "操作失败，请稍后重试"),
        );
      }

      onError?.(error, variables, context);
    },
  });
}
