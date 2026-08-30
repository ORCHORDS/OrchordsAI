import { afterEach, describe, expect, test } from "bun:test";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { recordTrace } from "../src/recorder";
import type { LoadedTraceCase } from "../src/types";

const temporaryDirectories: string[] = [];

afterEach(async () => {
  delete process.env.TRACE_TEST_API_KEY;
  await Promise.all(temporaryDirectories.splice(0).map((path) => rm(path, {
    recursive: true,
    force: true,
  })));
});

describe("recordTrace", () => {
  test("records an SSE response without writing the API key", async () => {
    const authorizationHeaders: Array<string | null> = [];
    const request = async (_url: string | URL, init: RequestInit) => {
      authorizationHeaders.push(new Headers(init.headers).get("authorization"));
      return new Response([
        "event: response.output_text.delta\n",
        "data: {\"delta\":\"hello\"}\n\n",
        "event: response.completed\n",
        "data: {\"type\":\"response.completed\"}\n\n",
      ].join(""), {
        headers: { "Content-Type": "text/event-stream" },
      });
    };

    try {
      const directory = await mkdtemp(join(tmpdir(), "rikkahub-trace-cli-"));
      temporaryDirectories.push(directory);
      const outputPath = join(directory, "events.jsonl");
      process.env.TRACE_TEST_API_KEY = "test-secret";
      const trace: LoadedTraceCase = {
        name: "local-test",
        provider: "openai-responses",
        model: "test-model",
        apiKeyEnv: "TRACE_TEST_API_KEY",
        baseUrl: "https://example.com/",
        endpoint: "/trace",
        headers: {},
        body: { input: "hello" },
        outputPath,
        timeoutMs: 5_000,
      };

      expect(await recordTrace(trace, false, request)).toBe(2);
      expect(authorizationHeaders).toEqual(["Bearer test-secret"]);
      const output = await readFile(outputPath, "utf8");
      expect(output).not.toContain("test-secret");
      expect(output.trim().split("\n").map((line) => JSON.parse(line))).toEqual([
        {
          event: "response.output_text.delta",
          data: "{\"delta\":\"hello\"}",
        },
        {
          event: "response.completed",
          data: "{\"type\":\"response.completed\"}",
        },
      ]);
    } finally {
      delete process.env.TRACE_TEST_API_KEY;
    }
  });

  test("normalizes node http AbortError into a timeout error", async () => {
    // Simulate the real node:http behavior on signal-aborted requests: a
    // plain Error with .name === "AbortError" and .code === "ABORT_ERR".
    const abortError: NodeJS.ErrnoException = new Error("aborted");
    abortError.name = "AbortError";
    abortError.code = "ABORT_ERR";
    const request = async () => {
      throw abortError;
    };

    const trace: LoadedTraceCase = {
      name: "timeout-test",
      provider: "openai-responses",
      model: "test-model",
      apiKeyEnv: "TRACE_TEST_API_KEY",
      baseUrl: "https://example.com/",
      endpoint: "/trace",
      headers: {},
      body: { input: "hello" },
      outputPath: join(tmpdir(), "never-written.jsonl"),
      timeoutMs: 250,
    };

    process.env.TRACE_TEST_API_KEY = "test-secret";
    try {
      await expect(recordTrace(trace, false, request)).rejects.toThrow(
        /timed out after 250ms/,
      );
    } finally {
      delete process.env.TRACE_TEST_API_KEY;
    }
  });
});
