package com.orchords.orchordsai.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectorPolicyTest {
    private val action = ConnectorActionDefinition("github", "github.issue.comment", ConnectorRisk.WRITE, setOf("issues.write"))
    private val connection = ConnectorConnectionSnapshot("c1", "github", "a1", 2, true, setOf("issues.write"))
    private val request = ConnectorInvocation("r1", "actor1", "c1", action.actionId, "repo1/issue1", "a".repeat(64))
    private val access = ConnectorAccessSnapshot(setOf("c1"), setOf(action.actionId), setOf(request.resourceId), true, "actor1")
    private val approval = ConnectorApproval("p1", request, "a1", 2, 100, 300)

    @Test
    fun writeNeedsExactCurrentApproval() {
        assertEquals(ConnectorDecision.APPROVAL_REQUIRED,
            evaluateConnectorInvocation(action, connection, request, access, null, true, 200))
        assertEquals(ConnectorDecision.ALLOW,
            evaluateConnectorInvocation(action, connection, request, access, approval, true, 200))
        assertEquals(ConnectorDecision.APPROVAL_MISMATCH,
            evaluateConnectorInvocation(action, connection, request.copy(argumentDigest = "b".repeat(64)), access, approval, true, 200))
        assertEquals(ConnectorDecision.APPROVAL_MISMATCH,
            evaluateConnectorInvocation(action.copy(version = 2), connection, request, access, approval, true, 200))
        assertEquals(ConnectorDecision.APPROVAL_EXPIRED,
            evaluateConnectorInvocation(action, connection, request, access, approval, true, 300))
    }

    @Test
    fun approvalDoesNotOverrideRuntimeOrAccountAuthority() {
        assertEquals(ConnectorDecision.ADAPTER_UNAVAILABLE,
            evaluateConnectorInvocation(action, connection, request, access, approval, false, 200))
        assertEquals(ConnectorDecision.CONNECTION_UNAVAILABLE,
            evaluateConnectorInvocation(action, connection.copy(connected = false), request, access, approval, true, 200))
        assertEquals(ConnectorDecision.POLICY_DENIED,
            evaluateConnectorInvocation(action, connection, request, access.copy(actorId = "actor2"), approval, true, 200))
        assertEquals(ConnectorDecision.SCOPE_DENIED,
            evaluateConnectorInvocation(action, connection.copy(grantedCapabilities = emptySet()), request, access, approval, true, 200))
        assertEquals(ConnectorDecision.APPROVAL_MISMATCH,
            evaluateConnectorInvocation(action, connection.copy(accountId = "a2"), request, access, approval, true, 200))
    }
}
