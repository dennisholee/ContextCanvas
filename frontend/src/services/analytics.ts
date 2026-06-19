import { config } from '../config'

type AnalyticsEvent =
  | 'message_sent'
  | 'a2ui_component_rendered'
  | 'query_executed'
  | 'query_recovery_attempted'
  | 'query_recovery_succeeded'
  | 'schema_introspected'
  | 'proactive_insight_shown'
  | 'confirmation_card_shown'
  | 'confirmation_approved'
  | 'confirmation_cancelled'
  | 'error_card_shown'

/**
 * Lightweight analytics wrapper.
 * In production, this sends events to PostHog.
 * For the PoC, it logs to console in dev mode.
 */
export function trackEvent(event: AnalyticsEvent, properties?: Record<string, unknown>) {
  if (config.TEST_MODE) return

  if (import.meta.env.DEV) {
    console.debug(`[Analytics] ${event}`, properties ?? '')
  }

  // PostHog integration (add when POSTHOG_KEY is configured)
  if (config.POSTHOG_KEY) {
    try {
      // In production, this would call: posthog.capture(event, properties)
      // window.posthog?.capture(event, properties)
    } catch {
      // Silently fail — analytics should never break the app
    }
  }
}