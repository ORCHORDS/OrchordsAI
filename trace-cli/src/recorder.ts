import { access, mkdir, open, rename, rm } from "node:fs/promises";
import { dirname } from "node:path";
import { buildProviderRequest, defaultApiKeyEnv } from "./providers";
import { requestPublicUrl } from "./ssrf";
import { parseSseStream } from "./sse";
import type { LoadedTraceCase } from "./types";

export async function recordTrace(
  trace: LoadedTraceCase,
  force: boolean,
  makeRequest: typeof requestPublicUrl = requestPublicUrl,
): Promise<number> {
  const apiKeyEnv = trace.apiKeyEnv ?? defaultApiKeyEnv(trace.provider);
  const apiKey = process.env[apiKeyEnv];
  if (!apiKey) {
    throw new Error(`${trace.name}: environment variable ${apiKeyEnv} is not set`);
  }

  if (!force && await exists(trace.outputPath)) {
    throw new Error(`${trace.name}: output already exists: ${trace.outputPath} (use --force)`);
  }

  const request = buildProviderRequest(trace, apiKey);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), trace.timeoutMs);
  const temporaryPath = `${trace.outputPath}.tmp-${process.pid}`;
  let handle: Awaited<ReturnType<typeof open>> | undefined;

  try {
    const response = await makeRequest(request.url, {
      method: "POST",
      headers: request.headers,
      body: JSON.stringify(request.body),
      signal: controller.signal,
    });
    if (!response.ok) {
      const errorBody = (await response.text()).slice(0, 8_192);
      throw new Error(`${trace.name}: HTTP ${response.status} ${response.statusText}\n${errorBody}`);
    }
    const contentType = response.headers.get("content-type") ?? "";
    if (!contentType.toLowerCase().includes("text/event-stream")) {
      const body = (await response.text()).slice(0, 8_192);
      throw new Error(`${trace.name}: expected text/event-stream, got ${contentType}\n${body}`);
    }
    if (!response.body) throw new Error(`${trace.name}: response body is empty`);

    await mkdir(dirname(trace.outputPath), { recursive: true });
    await rm(temporaryPath, { force: true });
    handle = await open(temporaryPath, "wx");
    let eventCount = 0;
    for await (const event of parseSseStream(response.body)) {
      await handle.write(`${JSON.stringify(event)}\n`);
      eventCount += 1;
    }
    await handle.sync();
    await handle.close();
    handle = undefined;
    await rename(temporaryPath, trace.outputPath);
    return eventCount;
  } catch (error) {
    await handle?.close().catch(() => undefined);
    await rm(temporaryPath, { force: true });
    if (isAbortError(error)) {
      throw new Error(`${trace.name}: request timed out after ${trace.timeoutMs}ms`);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function isAbortError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  // node:http / node:https signal-aborted requests emit an Error with
  // .name === "AbortError" and .code === "ABORT_ERR". DOMException is
  // surfaced by fetch, but we don't use fetch in this code path.
  if (error.name === "AbortError") return true;
  const code = (error as NodeJS.ErrnoException).code;
  return code === "ABORT_ERR" || code === "ERR_ABORTED";
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}
