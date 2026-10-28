# Roadmap

- v1: phases 1, 2, 3, 6. Complete offline gate, then owner-run phone/household acceptance and attended signing/publication.
- Acceptance: two-phone token coexistence; process restart; certificate changes; multicast denial/manual fallback; wake from standby; app buttons open each app from Home and from an already-running app; two successive signed upgrades preserve pairing.
- Measure release p95 tap-to-write <50 ms, visible response <150 ms and paired cold-open ready <2 seconds. Record device/model/sample counts; exclude approval/discovery/wake. These remain unmeasured without devices.
- v2 only: ANU helper, owner enrollment, AdGuard policies, durable timers. Owner package identity reserves the boundary; no placeholder authority or secrets ship in v1.
