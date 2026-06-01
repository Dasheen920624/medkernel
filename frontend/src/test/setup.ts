// MedKernel v1.0 GA · Vitest 全局 setup
// 接入 @testing-library/jest-dom 的 toBeInTheDocument 等 matchers
import "@testing-library/jest-dom";
// Antd 5 内部用 window.matchMedia（responsive breakpoint），jsdom 不实现，需 mock
if (typeof window !== "undefined" && !window.matchMedia) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// ResizeObserver 在 Antd 5 + rc-* 里也常用，jsdom 不实现
if (typeof window !== "undefined" && typeof window.ResizeObserver === "undefined") {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

// Antd 5 的 CSS-in-JS 在 jsdom/nwsapi 下偶尔会查询浏览器可接受、
// 但 nwsapi 不接受的复杂选择器。测试环境返回空集合即可；真实浏览器不走这里。
if (typeof window !== "undefined") {
  const originalGetComputedStyle = window.getComputedStyle;
  window.getComputedStyle = (elt: Element, _pseudoElt?: string | null) =>
    originalGetComputedStyle(elt);

  const isSelectorSyntaxError = (error: unknown) =>
    error instanceof SyntaxError ||
    (error instanceof DOMException && error.name === "SyntaxError") ||
    (error instanceof Error && error.name === "SyntaxError");

  const originalQuerySelectorAll = Element.prototype.querySelectorAll;
  Element.prototype.querySelectorAll = function safeQuerySelectorAll(selectors: string) {
    try {
      return originalQuerySelectorAll.call(this, selectors);
    } catch (error) {
      if (isSelectorSyntaxError(error)) {
        return document.createDocumentFragment().querySelectorAll("*");
      }
      throw error;
    }
  };

  const originalMatches = Element.prototype.matches;
  Element.prototype.matches = function safeMatches(selectors: string) {
    try {
      return originalMatches.call(this, selectors);
    } catch (error) {
      if (isSelectorSyntaxError(error)) {
        return false;
      }
      throw error;
    }
  };
}
