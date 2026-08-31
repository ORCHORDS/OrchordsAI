import { describe, expect, test } from "bun:test";
import {
  requestPublicUrl,
  resolvePublicAddresses,
  validateOutboundUrl,
  type DnsResolver,
  type PinnedRequest,
} from "../src/ssrf";

const publicV4 = { address: "93.184.216.34", family: 4 as const };
const publicV6 = { address: "2606:4700:4700::1111", family: 6 as const };

function resolver(...answers: Array<{ address: string; family: 4 | 6 }>): DnsResolver {
  return async () => answers;
}

describe("outbound URL validation", () => {
  test("accepts http and https public hosts", async () => {
    expect(validateOutboundUrl("https://example.com/trace").hostname).toBe("example.com");
    expect(await resolvePublicAddresses(new URL("http://example.com"), resolver(publicV4)))
      .toEqual([publicV4]);
  });

  test("rejects unsupported protocols and URL credentials", () => {
    expect(() => validateOutboundUrl("file:///etc/passwd")).toThrow("http or https");
    expect(() => validateOutboundUrl("ftp://example.com/file")).toThrow("http or https");
    expect(() => validateOutboundUrl("https://user:pass@example.com")).toThrow("credentials");
  });

  test("rejects localhost and private or reserved IPv4 literals", () => {
    for (const target of [
      "http://localhost",
      "http://api.localhost",
      "http://0.0.0.0",
      "http://10.0.0.1",
      "http://100.64.0.1",
      "http://127.0.0.1",
      "http://169.254.169.254",
      "http://172.16.0.1",
      "http://192.168.0.1",
      "http://192.0.2.1",
      "http://198.18.0.1",
      "http://198.51.100.1",
      "http://203.0.113.1",
      "http://224.0.0.1",
      "http://240.0.0.1",
    ]) {
      expect(() => validateOutboundUrl(target)).toThrow();
    }
  });

  test("rejects non-public IPv6 and accepts a global IPv6 literal", () => {
    for (const target of [
      "http://[::]",
      "http://[::1]",
      "http://[::ffff:127.0.0.1]",
      "http://[64:ff9b::7f00:1]",
      "http://[2001:db8::1]",
      "http://[fc00::1]",
      "http://[fe80::1]",
      "http://[ff00::1]",
    ]) {
      expect(() => validateOutboundUrl(target)).toThrow();
    }
    expect(validateOutboundUrl("https://[2606:4700:4700::1111]/").hostname)
      .toContain("2606:4700:4700::1111");
  });

  test("rejects a hostname if any DNS answer is non-public", async () => {
    await expect(resolvePublicAddresses(
      new URL("https://example.com"),
      resolver(publicV4, { address: "10.0.0.8", family: 4 }),
    )).rejects.toThrow("non-public");
  });
});

describe("pinned outbound requests", () => {
  test("pins the validated address and does not resolve again", async () => {
    let resolutionCount = 0;
    const rebindingResolver: DnsResolver = async () => {
      resolutionCount += 1;
      return resolutionCount === 1
        ? [publicV4]
        : [{ address: "127.0.0.1", family: 4 }];
    };
    let connectedAddress: string | undefined;
    const request: PinnedRequest = async (_url, pinned) => {
      connectedAddress = pinned.address;
      return new Response("ok", { status: 200 });
    };

    const response = await requestPublicUrl(
      "https://example.com/trace",
      { method: "POST", body: "{}" },
      { resolve: rebindingResolver, request },
    );

    expect(await response.text()).toBe("ok");
    expect(resolutionCount).toBe(1);
    expect(connectedAddress).toBe(publicV4.address);
  });

  test("supports a validated global IPv6 DNS answer", async () => {
    let pinnedFamily: number | undefined;
    await requestPublicUrl("https://example.com", {}, {
      resolve: resolver(publicV6),
      request: async (_url, pinned) => {
        pinnedFamily = pinned.family;
        return new Response("ok");
      },
    });
    expect(pinnedFamily).toBe(6);
  });

  test("rejects redirects without following their target", async () => {
    let requestCount = 0;
    await expect(requestPublicUrl("https://example.com/redirect", {}, {
      resolve: resolver(publicV4),
      request: async () => {
        requestCount += 1;
        return new Response(null, {
          status: 302,
          headers: { Location: "http://127.0.0.1/admin" },
        });
      },
    })).rejects.toThrow("redirect");
    expect(requestCount).toBe(1);
  });

  test("does not start a request when DNS resolves to a private address", async () => {
    let requested = false;
    await expect(requestPublicUrl("https://example.com", {}, {
      resolve: resolver({ address: "192.168.1.20", family: 4 }),
      request: async () => {
        requested = true;
        return new Response("unexpected");
      },
    })).rejects.toThrow("non-public");
    expect(requested).toBe(false);
  });

  test("rejects literal IPv6 addresses outside the global-unicast range", async () => {
    const reservedV6 = [
      "4000::1",            // outside 2000::/3 (RFC 4291 2.5.4)
      "100::1",             // RFC 6666 discard-only
      "2001:db8::1",        // documentation (RFC 3849)
      "2001::1",            // IETF protocol assignments (RFC 2928)
      "2002:0908:0908::1",  // 6to4 (RFC 3056)
      "3fff::1",            // benchmarking (RFC 2544)
      "fc00::1",            // unique-local (RFC 4193)
      "fe80::1",            // link-local
      "fec0::1",            // site-local (deprecated)
      "ff02::1",            // multicast
      "::1",                // loopback
      "::",                 // unspecified
    ];
    for (const address of reservedV6) {
      let requested = false;
      await expect(requestPublicUrl("https://example.com", {}, {
        resolve: resolver({ address, family: 6 }),
        request: async () => {
          requested = true;
          return new Response("unexpected");
        },
      })).rejects.toThrow("non-public");
      expect(requested).toBe(false);
    }
  });

  test("accepts a global-unicast IPv6 literal that is not in any reserved subrange", async () => {
    let requested = false;
    await requestPublicUrl("https://example.com", {}, {
      resolve: resolver({ address: "2606:4700:4700::1111", family: 6 }),
      request: async () => {
        requested = true;
        return new Response("ok");
      },
    });
    expect(requested).toBe(true);
  });
});
