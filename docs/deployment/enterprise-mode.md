# Enterprise Mode Deployment Notes

Full Docker deployment uses backend runtime feature flags plus frontend build-time defaults.

Required defaults for the full deployment:

- `SEAHORSE_AGENT_PRODUCT_MODE=enterprise-platform`
- `VITE_SEAHORSE_PRODUCT_MODE=enterprise-platform`
- `VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN=true`
- Every `SEAHORSE_AGENT_ADVANCED_*_ENABLED` flag for admin modules should be set to `true` unless the deployment intentionally hides that module.

The frontend still reads `/api/features` after startup. The Vite variables are only the initial build fallback; the backend capability response is the source of truth for menus, route guards, and unavailable states.
