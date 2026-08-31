# Chat Generation Pipeline

The UI submits content, repositories load state, transformers prepare context, and a provider adapter sends the request. Streaming events become shared message parts; tool calls pass binding and approval checks; completion, cancellation, or error finalizes persisted state. Keep provider formats isolated and add regression tests.
