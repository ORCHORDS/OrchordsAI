package com.orchords.orchordsai.data.extensions

fun main() {
    var checks = 0
    val read = ConnectorActionDefinition("github", "github.issue.read", ConnectorRisk.READ, setOf("issues.read"))
    val write = ConnectorActionDefinition("github", "github.issue.comment", ConnectorRisk.WRITE, setOf("issues.write"))
    val connection = ConnectorConnectionSnapshot("connection-1", "github", "account-1", 3, true, setOf("issues.read", "issues.write"))
    val request = ConnectorInvocation("request-1", "assistant-1", "connection-1", "github.issue.read", "repo-1/issue-2", "a".repeat(64))
    val policy = ConnectorAccessSnapshot(setOf("connection-1"), setOf(read.actionId, write.actionId), setOf("repo-1/issue-2"), true, "assistant-1")
    fun expect(wanted: ConnectorDecision, actual: ConnectorDecision) { checks++; check(wanted == actual) { "Expected $wanted, got $actual" } }
    fun decision(action: ConnectorActionDefinition = read, conn: ConnectorConnectionSnapshot? = connection,
                 req: ConnectorInvocation = request, access: ConnectorAccessSnapshot = policy,
                 approval: ConnectorApproval? = null, adapter: Boolean = true, now: Long = 1000) =
        evaluateConnectorInvocation(action, conn, req, access, approval, adapter, now)
    expect(ConnectorDecision.ALLOW, decision())
    expect(ConnectorDecision.ADAPTER_UNAVAILABLE, decision(adapter = false))
    expect(ConnectorDecision.CONNECTION_UNAVAILABLE, decision(conn = null))
    expect(ConnectorDecision.CONNECTION_UNAVAILABLE, decision(conn = connection.copy(connected = false)))
    expect(ConnectorDecision.IDENTITY_MISMATCH, decision(conn = connection.copy(connectorId = "gmail")))
    expect(ConnectorDecision.IDENTITY_MISMATCH, decision(req = request.copy(connectionId = "other")))
    expect(ConnectorDecision.IDENTITY_MISMATCH, decision(req = request.copy(actionId = "invented")))
    expect(ConnectorDecision.INVALID_REQUEST, decision(req = request.copy(argumentDigest = "not-a-digest")))
    expect(ConnectorDecision.INVALID_REQUEST, decision(req = request.copy(actorId = "")))
    expect(ConnectorDecision.POLICY_DENIED, decision(access = policy.copy(runtimeAllowed = false)))
    expect(ConnectorDecision.POLICY_DENIED, decision(access = policy.copy(connectionIds = emptySet())))
    expect(ConnectorDecision.POLICY_DENIED, decision(access = policy.copy(actionIds = emptySet())))
    expect(ConnectorDecision.POLICY_DENIED, decision(access = policy.copy(resourceIds = emptySet())))
    expect(ConnectorDecision.SCOPE_DENIED, decision(conn = connection.copy(grantedCapabilities = emptySet())))
    val writeRequest = request.copy(actionId = write.actionId)
    val approval = ConnectorApproval("approval-1", writeRequest, "account-1", 3, 900, 1100)
    expect(ConnectorDecision.APPROVAL_REQUIRED, decision(action = write, req = writeRequest))
    expect(ConnectorDecision.ALLOW, decision(action = write, req = writeRequest, approval = approval))
    expect(ConnectorDecision.APPROVAL_MISMATCH, decision(action = write, req = writeRequest, approval = approval.copy(accountId = "other")))
    expect(ConnectorDecision.APPROVAL_MISMATCH, decision(action = write, req = writeRequest, approval = approval.copy(connectionRevision = 2)))
    expect(ConnectorDecision.APPROVAL_MISMATCH, decision(action = write, req = writeRequest.copy(argumentDigest = "b".repeat(64)), approval = approval))
    expect(ConnectorDecision.POLICY_DENIED, decision(action = write, req = writeRequest.copy(actorId = "other"), approval = approval))
    expect(ConnectorDecision.APPROVAL_MISMATCH, decision(action = write, req = writeRequest.copy(requestId = "other"), approval = approval))
    expect(ConnectorDecision.APPROVAL_EXPIRED, decision(action = write, req = writeRequest, approval = approval, now = 1100))
    expect(ConnectorDecision.APPROVAL_EXPIRED, decision(action = write, req = writeRequest, approval = approval, now = 800))
    expect(ConnectorDecision.SCOPE_DENIED, decision(action = write, req = writeRequest, approval = approval, conn = connection.copy(grantedCapabilities = setOf("issues.read"))))
    for (risk in listOf(ConnectorRisk.DESTRUCTIVE, ConnectorRisk.PAID)) {
        expect(ConnectorDecision.APPROVAL_REQUIRED, decision(action = write.copy(risk = risk), req = writeRequest))
    }
    expect(ConnectorDecision.APPROVAL_MISMATCH, decision(action = write.copy(version = 2), req = writeRequest, approval = approval))
    expect(ConnectorDecision.APPROVAL_MISMATCH, decision(action = write.copy(risk = ConnectorRisk.PAID), req = writeRequest, approval = approval))
    expect(ConnectorDecision.POLICY_DENIED, decision(access = policy.copy(actorId = "other")))
    expect(ConnectorDecision.INVALID_REQUEST, decision(now = -1))
    expect(ConnectorDecision.INVALID_REQUEST, decision(req = request.copy(resourceId = "bad\nresource")))
    expect(ConnectorDecision.APPROVAL_EXPIRED, decision(action = write, req = writeRequest, approval = approval.copy(issuedAtMillis = -1)))
    println("Connector policy checks passed: $checks assertions")
}
