import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";
import { Readable } from "node:stream";

export interface ResolvedAddress {
  address: string;
  family: 4 | 6;
}

export type DnsResolver = (hostname: string) => Promise<ResolvedAddress[]>;
export type PinnedRequest = (
  url: URL,
  address: ResolvedAddress,
  init: RequestInit,
) => Promise<Response>;

interface RequestPublicUrlDependencies {
  resolve?: DnsResolver;
  request?: PinnedRequest;
}

const defaultResolver: DnsResolver = async (hostname) => {
  const addresses = await lookup(hostname, { all: true, verbatim: true });
  return addresses.map(({ address, family }) => ({
    address,
    family: family as 4 | 6,
  }));
};

export function validateOutboundUrl(value: string | URL): URL {
  let url: URL;
  try {
    url = value instanceof URL ? new URL(value) : new URL(value);
  } catch {
    throw new Error("Outbound URL is invalid");
  }

  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Outbound URL must use http or https");
  }
  if (url.username || url.password) {
    throw new Error("Outbound URL must not contain credentials");
  }

  const hostname = normalizedHostname(url);
  if (!hostname) throw new Error("Outbound URL must contain a hostname");
  if (isLocalhostName(hostname)) throw new Error("Outbound URL must not target localhost");

  const family = isIP(hostname);
  if (family !== 0 && !isPublicIpAddress(hostname)) {
    throw new Error(`Outbound URL resolves to a non-public address: ${hostname}`);
  }
  return url;
}

export async function resolvePublicAddresses(
  url: URL,
  resolve: DnsResolver = defaultResolver,
): Promise<ResolvedAddress[]> {
  const hostname = normalizedHostname(url);
  const literalFamily = isIP(hostname);
  const addresses = literalFamily === 0
    ? await resolve(hostname)
    : [{ address: hostname, family: literalFamily as 4 | 6 }];

  if (addresses.length === 0) {
    throw new Error(`Outbound hostname did not resolve: ${hostname}`);
  }
  for (const resolved of addresses) {
    if (resolved.family !== 4 && resolved.family !== 6) {
      throw new Error(`Outbound hostname resolved with an invalid address family: ${resolved.family}`);
    }
    if (isIP(resolved.address) !== resolved.family || !isPublicIpAddress(resolved.address)) {
      throw new Error(`Outbound hostname resolves to a non-public address: ${resolved.address}`);
    }
  }
  return addresses;
}

export async function requestPublicUrl(
  value: string | URL,
  init: RequestInit,
  dependencies: RequestPublicUrlDependencies = {},
): Promise<Response> {
  const url = validateOutboundUrl(value);
  const addresses = await resolvePublicAddresses(url, dependencies.resolve ?? defaultResolver);
  const request = dependencies.request ?? pinnedHttpRequest;
  const response = await request(url, addresses[0]!, { ...init, redirect: "manual" });

  if (response.status >= 300 && response.status < 400) {
    await response.body?.cancel().catch(() => undefined);
    throw new Error(`Outbound request redirect is not allowed (HTTP ${response.status})`);
  }
  return response;
}

export function isPublicIpAddress(address: string): boolean {
  const family = isIP(address);
  if (family === 4) return isPublicIpv4(address);
  if (family === 6) return isPublicIpv6(address);
  return false;
}

function isPublicIpv4(address: string): boolean {
  const parts = parseIpv4(address);
  if (!parts) return false;
  const value = ipv4Number(parts);
  return !IPV4_NON_PUBLIC.some(([base, bits]) => inIpv4Range(value, base, bits));
}

function isPublicIpv6(address: string): boolean {
  const bytes = parseIpv6(address);
  if (!bytes) return false;

  // IPv4-mapped IPv6 addresses (::ffff:a.b.c.d) defer to IPv4 rules.
  if (
    matchesIpv6Prefix(bytes, "00000000000000000000", 80)
    && bytes[10] === 0xff
    && bytes[11] === 0xff
  ) {
    return isPublicIpv4(Array.from(bytes.slice(12)).join("."));
  }
  // IPv4-compatible but non-mapped (::a.b.c.d) is reserved.
  if (matchesIpv6Prefix(bytes, "000000000000000000000000", 96)) return false;

  // Global-unicast is `2000::/3` per RFC 4291 2.5.4. Anything outside is non-public.
  if (!matchesIpv6Prefix(bytes, "20000000000000000000000000000000", 3)) return false;

  // Subtract the documented special-purpose ranges inside 2000::/3.
  return !IPV6_NON_PUBLIC.some(([prefix, bits]) => matchesIpv6Prefix(bytes, prefix, bits));
}

function normalizedHostname(url: URL): string {
  const hostname = url.hostname.startsWith("[") && url.hostname.endsWith("]")
    ? url.hostname.slice(1, -1)
    : url.hostname;
  return hostname.toLowerCase().replace(/\.$/, "");
}

function isLocalhostName(hostname: string): boolean {
  return hostname === "localhost"
    || hostname.endsWith(".localhost")
    || hostname === "localhost.localdomain"
    || hostname === "ip6-localhost"
    || hostname === "ip6-loopback";
}

function parseIpv4(address: string): number[] | undefined {
  const parts = address.split(".");
  if (parts.length !== 4) return undefined;
  const numbers = parts.map(Number);
  return numbers.every((part) => Number.isInteger(part) && part >= 0 && part <= 255)
    ? numbers
    : undefined;
}

function ipv4Number(parts: number[]): number {
  return (((parts[0]! * 256 + parts[1]!) * 256 + parts[2]!) * 256 + parts[3]!) >>> 0;
}

function inIpv4Range(value: number, base: string, prefixBits: number): boolean {
  const baseParts = parseIpv4(base);
  if (!baseParts) return false;
  const mask = prefixBits === 0 ? 0 : (0xffffffff << (32 - prefixBits)) >>> 0;
  return (value & mask) === (ipv4Number(baseParts) & mask);
}

function parseIpv6(address: string): Uint8Array | undefined {
  const withoutZone = address.split("%")[0]!;
  const halves = withoutZone.split("::");
  if (halves.length > 2) return undefined;

  const left = parseIpv6Groups(halves[0] ?? "");
  const right = parseIpv6Groups(halves[1] ?? "");
  if (!left || !right) return undefined;
  const missing = 8 - left.length - right.length;
  if ((halves.length === 1 && missing !== 0) || (halves.length === 2 && missing < 1)) return undefined;

  const groups = [...left, ...Array(Math.max(0, missing)).fill(0), ...right];
  if (groups.length !== 8) return undefined;
  const bytes = new Uint8Array(16);
  groups.forEach((group, index) => {
    bytes[index * 2] = group >>> 8;
    bytes[index * 2 + 1] = group & 0xff;
  });
  return bytes;
}

function parseIpv6Groups(value: string): number[] | undefined {
  if (!value) return [];
  const groups: number[] = [];
  for (const group of value.split(":")) {
    if (group.includes(".")) {
      const ipv4 = parseIpv4(group);
      if (!ipv4) return undefined;
      groups.push((ipv4[0]! << 8) | ipv4[1]!, (ipv4[2]! << 8) | ipv4[3]!);
    } else {
      if (!/^[0-9a-f]{1,4}$/i.test(group)) return undefined;
      groups.push(Number.parseInt(group, 16));
    }
  }
  return groups;
}

function matchesIpv6Prefix(bytes: Uint8Array, prefixHex: string, bits: number): boolean {
  const prefix = Uint8Array.from(prefixHex.match(/.{2}/g)?.map((part) => Number.parseInt(part, 16)) ?? []);
  const wholeBytes = Math.floor(bits / 8);
  for (let index = 0; index < wholeBytes; index += 1) {
    if (bytes[index] !== prefix[index]) return false;
  }
  const remainingBits = bits % 8;
  if (remainingBits === 0) return true;
  const mask = 0xff << (8 - remainingBits);
  return (bytes[wholeBytes]! & mask) === (prefix[wholeBytes]! & mask);
}

const IPV4_NON_PUBLIC: ReadonlyArray<readonly [string, number]> = [
  ["0.0.0.0", 8],
  ["10.0.0.0", 8],
  ["100.64.0.0", 10],
  ["127.0.0.0", 8],
  ["169.254.0.0", 16],
  ["172.16.0.0", 12],
  ["192.0.0.0", 24],
  ["192.0.2.0", 24],
  ["192.88.99.0", 24],
  ["192.168.0.0", 16],
  ["198.18.0.0", 15],
  ["198.51.100.0", 24],
  ["203.0.113.0", 24],
  ["224.0.0.0", 4],
  ["240.0.0.0", 4],
];

const IPV6_NON_PUBLIC: ReadonlyArray<readonly [string, number]> = [
  // ::/128 unspecified
  ["00000000000000000000000000000000", 128],
  // ::1/128 loopback
  ["00000000000000000000000000000001", 128],
  // ::ffff:0:0/96 IPv4-mapped
  ["0064ff9b000000000000000000000000", 96],
  // 64:ff9b:1::/48 IPv4-IPv6 translation (RFC 6052)
  ["0064ff9b000100000000000000000000", 48],
  // 100::/64 discard-only (RFC 6666)
  ["01000000000000000000000000000000", 64],
  // 2001::/23 IETF protocol assignments (RFC 2928)
  ["20010000000000000000000000000000", 23],
  // 2001:db8::/32 documentation (RFC 3849)
  ["20010db8000000000000000000000000", 32],
  // 2002::/16 6to4 (RFC 3056) - cross-checked against IPv4 boundary
  ["20020000000000000000000000000000", 16],
  // 3fff::/20 benchmarking (RFC 2544)
  ["3fff0000000000000000000000000000", 20],
  // 5f00::/16 unique-local prefix (RFC 4193) - inside fc00::/7 but listed for clarity
  ["5f000000000000000000000000000000", 16],
  // fc00::/7 unique-local (RFC 4193)
  ["fc000000000000000000000000000000", 7],
  // fe80::/10 link-local
  ["fe800000000000000000000000000000", 10],
  // fec0::/10 site-local (deprecated)
  ["fec00000000000000000000000000000", 10],
  // ff00::/8 multicast
  ["ff000000000000000000000000000000", 8],
];

const pinnedHttpRequest: PinnedRequest = (url, pinned, init) => new Promise((resolve, reject) => {
  const transport = url.protocol === "https:" ? httpsRequest : httpRequest;
  const headers = Object.fromEntries(new Headers(init.headers).entries());
  const request = transport(url, {
    method: init.method,
    headers,
    signal: init.signal ?? undefined,
    lookup: (_hostname, _options, callback) => callback(null, pinned.address, pinned.family),
  }, (response) => {
    const responseHeaders = new Headers();
    for (let index = 0; index < response.rawHeaders.length; index += 2) {
      responseHeaders.append(response.rawHeaders[index]!, response.rawHeaders[index + 1]!);
    }
    resolve(new Response(Readable.toWeb(response) as unknown as ReadableStream<Uint8Array>, {
      status: response.statusCode ?? 500,
      statusText: response.statusMessage,
      headers: responseHeaders,
    }));
  });
  request.once("error", reject);

  if (init.body == null) {
    request.end();
  } else if (typeof init.body === "string" || init.body instanceof Uint8Array) {
    request.end(init.body);
  } else {
    request.destroy(new Error("Unsupported outbound request body type"));
  }
});
