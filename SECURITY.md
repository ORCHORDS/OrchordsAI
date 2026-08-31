<p align="center">
  <img src="https://raw.githubusercontent.com/ORCHORDS/docs/main/assets/1080x360.jpg" width="1080" alt="ORCHORDS — BUILD DIFFERENT.">
</p>

# ORCHORDS AI Security Policy

**Independent software studio founded in 2025.**

ORCHORDS AI takes security reports seriously. Please do not open a public issue for a suspected vulnerability.

## Reporting a vulnerability

Use GitHub's **Private vulnerability reporting / Security Advisories** for this repository. Include, when available:

- affected commit, version, component, or dependency;
- clear reproduction steps or a minimal proof of concept;
- expected and observed behavior;
- security impact and realistic attack prerequisites;
- suggested mitigation or fix;
- relevant logs with secrets and personal data removed.

Do **not** include API keys, access tokens, private conversation content, credentials, signing material, or personal data in a report.

## Coordinated disclosure

Please keep vulnerability details private until a fix is available and coordinated disclosure has been agreed. We will validate reports, assess severity and scope, prepare a remediation, and publish an advisory when appropriate.

## Security scope

Security-sensitive areas include authentication and OAuth flows, provider/API credentials, Android credential storage, MCP transports and tool execution, network requests, file handling, WebView/browser surfaces, update and release mechanisms, CI/CD workflows, dependency supply chain, and signing/release artifacts.

## Secrets

No production secrets belong in source control, issues, pull requests, Actions logs, screenshots, examples, or test fixtures. Any credential suspected of exposure must be revoked or rotated; deleting it from the latest commit alone is not sufficient.

## Supported code

Security fixes target the current `main` branch and supported published releases. Historical snapshots and unsupported development builds may not receive backports.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
