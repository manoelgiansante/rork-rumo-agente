# App Store Connect Pre-flight Check

Run a comprehensive pre-submission checklist for App Store Connect to catch common rejection reasons before submitting for review.

## When to Use

- Before clicking "Add for Review" on a new app version
- After uploading a new build to verify everything is configured
- When preparing an app for initial release
- After receiving a rejection to verify all issues are fixed

## Interactive Setup

The skill starts by asking about your app configuration:

1. Monetization model (Free / Paid / Freemium with IAP)
2. Paywall implementation (RevenueCat Paywalls / Custom / None)
3. iPad support (iPhone only / Universal)

Based on your answers, irrelevant checks are skipped. For example:

- Free apps skip IAP and RevenueCat validation
- iPhone-only apps skip iPad screenshot checks
- RC Paywalls users get dashboard validation instead of code analysis

The skill also auto-detects which MCPs are available:

- Claude in Chrome (required for all checks)
- RevenueCat MCP (optional, falls back to browser if unavailable)

## Checklist Items

Validates against Apple's App Review Guidelines:

- Guideline 2.3 - Accurate Metadata
- Guideline 3.1.1 - In-App Purchase
- Guideline 3.1.2 - Subscriptions
- Guideline 5.1.1 - Data Collection

### Version Metadata (Guideline 2.3)

- [ ] App name ≤30 characters
- [ ] Screenshots show app in use (not splash/login screens)
- [ ] Screenshots for all required device sizes
- [ ] Screenshots appropriate for 4+ age rating
- [ ] App previews use only actual app footage
- [ ] Description without trademarked terms or pricing
- [ ] Keywords relevant to app
- [ ] What's New describes changes specifically
- [ ] If IAP: Screenshots/description indicate purchase requirements

### In-App Purchases (Guideline 3.1.1)

- [ ] All IAPs in "Ready to Submit" status
- [ ] IAPs SELECTED and attached to this version (common rejection!)
- [ ] Review screenshots uploaded for new products
- [ ] Display names and descriptions appropriate for public
- [ ] No alternative payment mechanisms (license keys, QR codes)
- [ ] Loot box odds disclosed (if applicable)

### Subscriptions (Guideline 3.1.2)

- [ ] Subscription period ≥7 days
- [ ] Clear description of what subscriber receives
- [ ] Free trial configured via App Store Connect (if offered)
- [ ] Upgrade/downgrade prevents duplicate subscriptions
- [ ] Consumable currencies do not expire

### App Review Information

- [ ] Contact information provided and valid
- [ ] Demo credentials provided if login required
- [ ] Demo credentials actually work
- [ ] All new features described specifically in Review Notes
- [ ] Hidden/advanced features explained

### Privacy (Guideline 5.1.1)

- [ ] Privacy policy URL in App Store Connect
- [ ] Privacy policy accessible within app (both required)
- [ ] Privacy labels match actual data collection
- [ ] Age rating answered honestly

### Code Verification

- [ ] Restore purchases mechanism implemented
- [ ] No alternative payment bypass patterns
- [ ] Trial duration in code matches ASC config

## Instructions

1. Navigate to App Store Connect for the target app
2. Use Claude in Chrome to systematically check each section
3. Report findings as a checklist with pass/fail/warning status
4. Highlight blocking issues that will cause rejection
5. Note optional improvements

## Example Output

```
App Store Connect Pre-flight Check: MyApp v1.0

BLOCKING ISSUES (will cause rejection):
- In-App Purchases: 2 subscriptions not attached to version
- App Review: Sign-in credentials missing (app shows login screen)

WARNINGS (may cause rejection):
- Screenshots: iPad Pro 12.9" 6th gen missing (using 5th gen)
- Metadata: Promotional text empty

PASSED:
- Version metadata complete
- Privacy policy configured
- Age rating set
- Export compliance answered
- Build uploaded and selected
- Contact information provided

Recommendation: Fix blocking issues before submitting.
```

## Navigation Paths

- Version page: `/apps/{app_id}/distribution/ios/version/inflight`
- Subscriptions: `/apps/{app_id}/distribution/subscriptions`
- App Information: `/apps/{app_id}/distribution/app-info`
- Pricing: `/apps/{app_id}/distribution/pricing`
- App Privacy: `/apps/{app_id}/distribution/app-privacy`

## Model

Use `haiku` for speed - this is a checklist validation task, not complex reasoning.

## Apple Guidelines Reference

- Guideline 2.3 - Accurate Metadata: https://developer.apple.com/app-store/review/guidelines/#accurate-metadata
- Guideline 3.1.1 - In-App Purchase: https://developer.apple.com/app-store/review/guidelines/#in-app-purchase
- Guideline 3.1.2 - Subscriptions: https://developer.apple.com/app-store/review/guidelines/#subscriptions
- Guideline 5.1.1 - Data Collection: https://developer.apple.com/app-store/review/guidelines/#data-collection-and-storage
