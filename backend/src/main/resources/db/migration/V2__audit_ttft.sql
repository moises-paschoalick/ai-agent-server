-- Time to the first streamed piece of a model call; equal to latency_ms when the call did not stream.
ALTER TABLE audit_events ADD COLUMN ttft_ms bigint;
